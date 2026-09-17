package ru.heatmap.registry.service

import ru.heatmap.registry.domain.*
import ru.heatmap.registry.dto.*
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.PresetConstraintRepository
import ru.heatmap.registry.repository.PresetRoleRepository
import ru.heatmap.registry.repository.ToolDownloadRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.DuplicateSourceLabelException
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// "Ссылка на инструмент" должна быть уникальна, но только среди активных карточек - отклонённые
// и архивные не учитываются (см. AiToolRepository.findFirstBySourceLabelAndStatusIn), чтобы ту же
// ссылку можно было завести заново в новой заявке после отклонения/архивации прежней.
private val ACTIVE_SOURCE_LABEL_STATUSES = listOf(ToolStatus.PENDING, ToolStatus.PUBLISHED)

@Service
class ToolService(
    private val aiToolRepository: AiToolRepository,
    private val appUserRepository: AppUserRepository,
    private val toolRatingRepository: ToolRatingRepository,
    private val toolDownloadRepository: ToolDownloadRepository,
    private val toolNoteRepository: ToolNoteRepository,
    private val presetRoleRepository: PresetRoleRepository,
    private val presetConstraintRepository: PresetConstraintRepository,
    private val notificationService: NotificationService
) {

    /** Вкладки реестра: TOP - витрина лучших решений, остальные - этапы PDLC. */
    fun findByTab(
        tab: String,
        roles: List<String>?,
        framework: List<String>?,
        constraints: List<String>?,
        toolType: List<String>?,
        search: String?,
        sort: String?,
        principal: UserPrincipal?
    ): List<ToolResponse> {
        // Плашка "Топ" показывается на карточках независимо от активной вкладки, поэтому считаем
        // её один раз на весь запрос, а не заново для каждого найденного инструмента.
        val topIds = computeTopIds()
        val spec = listOfNotNull(
            statusSpec(ToolStatus.PUBLISHED),
            tabSpec(tab, topIds),
            collectionContainsAnySpec("roles", roles),
            // "Агентский фреймворк" и "Ограничения" - тоже коллекции, как и roles
            // (см. AiTool.framework/constraints), поэтому та же логика "содержит хотя бы
            // одно из выбранных значений".
            collectionContainsAnySpec("framework", framework),
            collectionContainsAnySpec("constraints", constraints),
            // "Тип инструмента" - у самого инструмента единственное значение (см. AiTool.toolType),
            // но в фильтре можно выбрать сразу несколько - совпадение с любым из них, как и у
            // ролей/фреймворка/ограничений выше, только без join (не коллекция, а обычная колонка).
            toolTypeSpec(toolType),
            searchSpec(search)
        ).fold(Specification.where<AiTool>(null)) { acc, next -> acc.and(next) }
        return aiToolRepository.findAll(spec, resolveSort(sort)).map { it.toResponse(principal, topIds) }
    }

    /**
     * Плашка "Топ" больше не выставляется вручную - она автоматически присваивается
     * не более чем 1% опубликованных инструментов с лучшим сочетанием оценки пользователей,
     * просмотров и скачиваний (при равенстве прочего оценка весит больше всего).
     */
    private fun computeTopIds(): Set<UUID> {
        val published = aiToolRepository.findAll(statusSpec(ToolStatus.PUBLISHED))
        if (published.isEmpty()) return emptySet()

        fun normalize(value: Double, min: Double, max: Double): Double =
            if (max > min) (value - min) / (max - min) else 0.0

        val ratings = published.map { if (it.ratingsCount > 0) it.ratingSum.toDouble() / it.ratingsCount else 0.0 }
        val views = published.map { it.views.toDouble() }
        val downloads = published.map { it.downloads.toDouble() }
        val ratingRange = (ratings.minOrNull() ?: 0.0) to (ratings.maxOrNull() ?: 0.0)
        val viewsRange = (views.minOrNull() ?: 0.0) to (views.maxOrNull() ?: 0.0)
        val downloadsRange = (downloads.minOrNull() ?: 0.0) to (downloads.maxOrNull() ?: 0.0)

        val scored = published.map { tool ->
            val avgRating = if (tool.ratingsCount > 0) tool.ratingSum.toDouble() / tool.ratingsCount else 0.0
            val score = 0.5 * normalize(avgRating, ratingRange.first, ratingRange.second) +
                0.25 * normalize(tool.views.toDouble(), viewsRange.first, viewsRange.second) +
                0.25 * normalize(tool.downloads.toDouble(), downloadsRange.first, downloadsRange.second)
            tool.id!! to score
        }

        val topCount = maxOf(1, kotlin.math.round(published.size * 0.01).toInt())
        return scored.sortedByDescending { it.second }.take(topCount).map { it.first }.toSet()
    }

    /** Сортировка списка реестра: по умолчанию - по эффективности, либо по явному выбору пользователя. */
    private fun resolveSort(sort: String?): org.springframework.data.domain.Sort {
        return when (sort?.uppercase()) {
            "CREATED_AT" -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("createdAt")
            )
            "UPDATED_AT" -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("updatedAt")
            )
            "VIEWS" -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("views")
            )
            else -> org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("efficiencyPct"),
                org.springframework.data.domain.Sort.Order.desc("downloads")
            )
        }
    }

    fun counts(): ToolCountsResponse {
        val published = ToolStatus.PUBLISHED
        return ToolCountsResponse(
            top = computeTopIds().size.toLong(),
            access = aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, published),
            usage = aiToolRepository.countByStageAndStatus(ToolStage.USAGE, published),
            habit = aiToolRepository.countByStageAndStatus(ToolStage.HABIT, published),
            standard = aiToolRepository.countByStageAndStatus(ToolStage.STANDARD, published),
            total = aiToolRepository.countByStatus(published)
        )
    }

    fun stats(): StatsResponse {
        val all = aiToolRepository.findAll(statusSpec(ToolStatus.PUBLISHED))
        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val newThisWeek = all.count { it.createdAt.isAfter(weekAgo) }
        return StatsResponse(
            totalTools = all.size.toLong(),
            newThisWeek = newThisWeek.toLong(),
            accessCount = all.count { it.stage == ToolStage.ACCESS }.toLong(),
            usageCount = all.count { it.stage == ToolStage.USAGE }.toLong(),
            standardCount = all.count { it.stage == ToolStage.STANDARD }.toLong()
        )
    }

    fun filterOptions(): FilterOptionsResponse {
        val all = aiToolRepository.findAll()
        // Базовые наборы (preset_role/preset_constraint - см. 010-create-preset-tables.sql)
        // видны в форме/фильтре всегда, даже если ни один инструмент ещё не использует
        // конкретное значение - плюс любые дополнительные значения, которые уже реально
        // использованы (например, введены администратором как "свой вариант" -
        // см. MultiSelectDropdown.allowCustom).
        val presetRoles = presetRoleRepository.findAll().map { it.role }
        val presetConstraints = presetConstraintRepository.findAll().map { it.value }
        return FilterOptionsResponse(
            roles = (presetRoles + all.flatMap { it.roles }).filter { it.isNotBlank() }.distinct().sorted(),
            // Фреймворк - множественное поле, как и "Ограничения" (см. AiTool.framework), в
            // список автодополнения/фильтра попадают только реально заполненные значения.
            // У фреймворка (в отличие от роли и ограничений) нет фиксированного базового набора.
            frameworks = all.flatMap { it.framework }.filter { it.isNotBlank() }.distinct().sorted(),
            constraints = (presetConstraints + all.flatMap { it.constraints }.filter { it.isNotBlank() })
                .distinct().sorted(),
            // Базовый набор TOOL_TYPE_OPTIONS (см. ToolDtos.kt) плюс реально сохранённые значения -
            // включая "свой вариант", который администратор мог ввести вручную (см.
            // AddToolModal/SelectDropdown.allowCustom), как и с ролями/ограничениями выше.
            toolTypes = (TOOL_TYPE_OPTIONS + all.mapNotNull { it.toolType }.filter { it.isNotBlank() })
                .distinct().sorted()
        )
    }

    fun findById(id: UUID, principal: UserPrincipal?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        return tool.toResponse(principal, computeTopIds())
    }

    @Transactional
    fun create(request: CreateToolRequest, principal: UserPrincipal): ToolResponse {
        val owner = appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
        val isAdmin = principal.role == "ADMIN"

        // Ссылка на инструмент обязательна для обычного пользователя, но необязательна для
        // администратора - он может завести карточку до появления публичной ссылки
        // (см. AiTool.sourceLabel/ToolCard - кнопка "Скачать" тогда показывает, что ссылки нет).
        if (!isAdmin && request.sourceLabel.isNullOrBlank()) {
            throw BadRequestException("Введите ссылку на инструмент")
        }
        val trimmedSourceLabel = request.sourceLabel?.trim()?.takeIf { it.isNotBlank() }
        if (trimmedSourceLabel != null) {
            aiToolRepository.findFirstBySourceLabelAndStatusIn(trimmedSourceLabel, ACTIVE_SOURCE_LABEL_STATUSES)
                ?.let { throw DuplicateSourceLabelException(it.id!!, it.name) }
        }

        // Обычный пользователь всегда создаёт заявку на модерацию с этапом Access - stage/status
        // из запроса ему не доступны. Администратор может сразу выставить этап и статус
        // ("На модерации" или "Опубликован" - ответ на уточняющий вопрос при постановке задачи).
        val stage = if (isAdmin) {
            request.stage?.let { parseEnum<ToolStage>(it, "этап") } ?: ToolStage.ACCESS
        } else {
            ToolStage.ACCESS
        }
        val status = if (isAdmin) {
            val requested = request.status?.let { parseEnum<ToolStatus>(it, "статус") } ?: ToolStatus.PENDING
            if (requested != ToolStatus.PENDING && requested != ToolStatus.PUBLISHED) {
                throw BadRequestException("При создании можно выставить только статус \"На модерации\" или \"Опубликован\"")
            }
            requested
        } else {
            ToolStatus.PENDING
        }

        val tool = AiTool(
            name = request.name.trim(),
            description = request.description.trim(),
            shortDescription = request.shortDescription?.trim()?.takeIf { it.isNotBlank() },
            stage = stage,
            status = status,
            roles = request.roles.toMutableSet(),
            framework = request.framework.map { it.trim() }.filter { it.isNotBlank() }.toMutableSet(),
            toolType = request.toolType?.trim()?.takeIf { it.isNotBlank() },
            constraints = request.constraints.map { it.trim() }.filter { it.isNotBlank() }.toMutableSet(),
            sourceLabel = trimmedSourceLabel,
            ownerName = owner.fullName,
            createdBy = owner
        )
        val saved = aiToolRepository.save(tool)
        // Если администратор публикует инструмент сразу (минуя модерацию) или прямо выставляет
        // "На модерации" вручную - это не обычная заявка, уведомлять администраторов не нужно
        // только когда инструмент опубликован сразу; в остальных случаях (обычный пользователь,
        // либо админ явно оставил "На модерации") заявка всё равно ждёт рассмотрения.
        if (saved.status == ToolStatus.PENDING) {
            notificationService.notifyAdminsOfNewSubmission(saved)
        }
        return saved.toResponse(principal, emptySet())
    }

    @Transactional
    fun update(id: UUID, request: UpdateToolRequest, principal: UserPrincipal): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        val isAdmin = principal.role == "ADMIN"
        val isOwner = tool.createdBy?.id == principal.id
        if (!isAdmin && !isOwner) {
            throw ForbiddenException("Недостаточно прав для редактирования этого инструмента")
        }
        // Автор может редактировать инструмент в любом статусе (PENDING/PUBLISHED/REJECTED/DRAFT) -
        // правки уже опубликованного, отклонённого или отозванного в черновик инструмента
        // отправляют его на повторную модерацию (см. ниже), поэтому запрещать редактирование
        // по статусу больше не нужно.
        val shouldResubmitOnEdit =
            tool.status == ToolStatus.PUBLISHED || tool.status == ToolStatus.REJECTED || tool.status == ToolStatus.DRAFT

        request.name?.let { tool.name = it.trim() }
        request.description?.let { tool.description = it.trim() }
        request.shortDescription?.let { tool.shortDescription = it.trim().takeIf { s -> s.isNotBlank() } }
        request.roles?.let { tool.roles = it.toMutableSet() }
        // Фреймворк - множественное поле, как и "Ограничения" ниже (см. AiTool.framework).
        request.framework?.let { tool.framework = it.map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableSet() }
        request.toolType?.let { tool.toolType = it.trim().takeIf { s -> s.isNotBlank() } }
        request.constraints?.let { tool.constraints = it.map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableSet() }
        // Ссылка обязательна для обычного пользователя (как и при создании), но необязательна
        // для администратора - пустая строка от него сохраняется как null.
        request.sourceLabel?.let {
            val trimmed = it.trim()
            if (!isAdmin && trimmed.isBlank()) {
                throw BadRequestException("Введите ссылку на инструмент")
            }
            val newSourceLabel = trimmed.takeIf { s -> s.isNotBlank() }
            if (newSourceLabel != null) {
                aiToolRepository.findFirstBySourceLabelAndStatusInAndIdNot(newSourceLabel, ACTIVE_SOURCE_LABEL_STATUSES, tool.id!!)
                    ?.let { existing -> throw DuplicateSourceLabelException(existing.id!!, existing.name) }
            }
            tool.sourceLabel = newSourceLabel
        }

        if (isAdmin) {
            request.stage?.let { tool.stage = parseEnum<ToolStage>(it, "этап") }
            request.status?.let { tool.status = parseEnum<ToolStatus>(it, "статус") }
            request.downloads?.let { tool.downloads = it }
            request.dau?.let { tool.dau = it }
            request.efficiencyPct?.let { tool.efficiencyPct = it }
            // "Топ" больше нельзя выставить вручную - плашка присваивается автоматически (см. computeTopIds).
            request.segment?.let { tool.segment = it.trim().takeIf { s -> s.isNotBlank() } }
        } else if (shouldResubmitOnEdit) {
            // Автор отредактировал уже опубликованный, отклонённый или отозванный в черновик
            // инструмент - отправляем его на повторную модерацию и сбрасываем прежнюю причину отклонения.
            tool.status = ToolStatus.PENDING
            tool.rejectionReason = null
            notificationService.notifyAdminsOfNewSubmission(tool)
        }
        tool.updatedAt = Instant.now()
        return aiToolRepository.save(tool).toResponse(principal, computeTopIds())
    }

    /**
     * Отзыв собственной заявки с модерации (кнопка "Отозвать" в виджете "Мои инструменты на
     * модерации" на главной) - вместо безвозвратного удаления переводит инструмент в статус
     * DRAFT. Черновик виден только автору (вкладка "Черновики" в "Мои инструменты" -
     * MyDownloadsModal), не публикуется и не участвует ни в одной выборке реестра, пока автор
     * не отредактирует его и не отправит повторно (см. update/shouldResubmitOnEdit выше) -
     * либо не удалит насовсем через обычный delete.
     */
    @Transactional
    fun withdraw(id: UUID, principal: UserPrincipal): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (tool.createdBy?.id != principal.id) {
            throw ForbiddenException("Отозвать можно только собственную заявку")
        }
        if (tool.status != ToolStatus.PENDING) {
            throw BadRequestException("Отозвать можно только заявку, ожидающую модерации")
        }
        tool.status = ToolStatus.DRAFT
        tool.updatedAt = Instant.now()
        return aiToolRepository.save(tool).toResponse(principal, emptySet())
    }

    @Transactional
    fun delete(id: UUID, principal: UserPrincipal) {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        val isAdmin = principal.role == "ADMIN"
        val isOwner = tool.createdBy?.id == principal.id
        // Администратор может удалить любой инструмент; автор - только собственные.
        if (!isAdmin && !isOwner) {
            throw ForbiddenException("Удалить можно только собственный инструмент")
        }
        aiToolRepository.delete(tool)
    }

    /** Счётчик просмотров увеличивается по клику "Подробнее" на карточке инструмента. */
    @Transactional
    fun incrementView(id: UUID, principal: UserPrincipal?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.views += 1
        return aiToolRepository.save(tool).toResponse(principal, computeTopIds())
    }

    /**
     * Счётчик скачиваний увеличивается по клику "Скачать" - вместе с переходом по ссылке на источник.
     * Учитываются только уникальные скачивания: повторный клик того же авторизованного пользователя
     * не увеличивает счётчик. У анонимных пользователей нет учётной записи, чтобы это отследить,
     * поэтому их скачивания считаются как есть.
     */
    @Transactional
    fun incrementDownload(id: UUID, principal: UserPrincipal?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (principal == null) {
            tool.downloads += 1
        } else if (!toolDownloadRepository.existsByToolIdAndUserId(id, principal.id)) {
            val owner = appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
            toolDownloadRepository.save(ToolDownload(tool = tool, user = owner))
            tool.downloads += 1
        }
        return aiToolRepository.save(tool).toResponse(principal, computeTopIds())
    }

    /**
     * Оценка инструмента пользователем по 5-балльной шкале (после скачивания).
     * Повторная оценка того же пользователя пересчитывает сумму, а не добавляет новую запись.
     */
    @Transactional
    fun rate(id: UUID, value: Int, principal: UserPrincipal): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        val existing = toolRatingRepository.findByToolIdAndUserId(id, principal.id)
        if (existing != null) {
            tool.ratingSum += (value - existing.value)
            existing.value = value
            existing.updatedAt = Instant.now()
        } else {
            val owner = appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
            toolRatingRepository.save(ToolRating(tool = tool, user = owner, value = value))
            tool.ratingSum += value
            tool.ratingsCount += 1
        }
        return aiToolRepository.save(tool).toResponse(principal, computeTopIds())
    }

    /** Инструменты, которые пользователь скачивал - для вкладки "Мои инструменты", где можно оценить/переоценить. */
    @Transactional(readOnly = true)
    fun findDownloaded(principal: UserPrincipal): List<ToolResponse> {
        val topIds = computeTopIds()
        return toolDownloadRepository.findByUserIdOrderByCreatedAtDesc(principal.id)
            .map { it.tool.toResponse(principal, topIds) }
    }

    /** Собственные инструменты пользователя, включая те, что ещё на модерации или отклонены. */
    fun findMine(principal: UserPrincipal): List<ToolResponse> {
        val spec = Specification<AiTool> { root, _, cb ->
            cb.equal(root.get<Any>("createdBy").get<UUID>("id"), principal.id)
        }
        val sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        val topIds = computeTopIds()
        return aiToolRepository.findAll(spec, sort).map { it.toResponse(principal, topIds) }
    }

    // Инструменты на модерации всегда PENDING - в число опубликованных топ-инструментов попасть не могут.
    // principal передаётся (хотя эндпоинт и так доступен только ADMIN), чтобы в ответе корректно
    // считался notesCount - он виден только администратору (см. toResponse).
    fun pendingModeration(principal: UserPrincipal): List<ToolResponse> =
        aiToolRepository.findAll(statusSpec(ToolStatus.PENDING)).map { it.toResponse(principal, emptySet()) }

    @Transactional
    fun approve(id: UUID): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.PUBLISHED
        tool.rejectionReason = null
        tool.updatedAt = Instant.now()
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = true, reason = null)
        return saved.toResponse(null, computeTopIds())
    }

    @Transactional
    fun reject(id: UUID, reason: String): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.REJECTED
        tool.rejectionReason = reason.trim()
        tool.updatedAt = Instant.now()
        // Отклонённый инструмент не может быть "Топ".
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = false, reason = tool.rejectionReason)
        return saved.toResponse(null, emptySet())
    }

    // Инструменты в архиве всегда PUBLISHED-in-the-past, но самим статусом ARCHIVED уже
    // не попадают в обычный реестр (см. statusSpec(PUBLISHED) в findByTab/computeTopIds) -
    // отдельная выборка нужна только для вкладки "Архив" в администрировании.
    fun archived(principal: UserPrincipal): List<ToolResponse> =
        aiToolRepository.findAll(statusSpec(ToolStatus.ARCHIVED)).map { it.toResponse(principal, emptySet()) }

    /** Комментарий необязателен (см. ArchiveToolRequest) - уведомление автору уходит в любом случае. */
    @Transactional
    fun archive(id: UUID, reason: String?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (tool.status != ToolStatus.PUBLISHED) {
            throw BadRequestException("Архивировать можно только опубликованный инструмент")
        }
        tool.status = ToolStatus.ARCHIVED
        tool.updatedAt = Instant.now()
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfArchive(saved, reason?.trim()?.takeIf { it.isNotBlank() })
        return saved.toResponse(null, emptySet())
    }

    // Восстановление всегда возвращает в PUBLISHED - архивировать можно только опубликованный
    // инструмент (см. archive выше), поэтому "восстановить" однозначно значит "опубликовать снова".
    @Transactional
    fun restore(id: UUID): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        if (tool.status != ToolStatus.ARCHIVED) {
            throw BadRequestException("Восстановить можно только архивированный инструмент")
        }
        tool.status = ToolStatus.PUBLISHED
        tool.updatedAt = Instant.now()
        return aiToolRepository.save(tool).toResponse(null, emptySet())
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fieldLabel: String): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw BadRequestException("Некорректное значение поля \"$fieldLabel\": $value") }

    private fun statusSpec(status: ToolStatus): Specification<AiTool> =
        Specification { root, _, cb -> cb.equal(root.get<ToolStatus>("status"), status) }

    private fun tabSpec(tab: String, topIds: Set<UUID>): Specification<AiTool>? =
        when (tab.uppercase()) {
            "TOP" -> Specification { root, _, _ -> root.get<UUID>("id").`in`(topIds) }
            "ACCESS" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.ACCESS) }
            "USAGE" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.USAGE) }
            "HABIT" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.HABIT) }
            "STANDARD" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.STANDARD) }
            else -> null
        }

    /** "Тип инструмента" в фильтре - совпадение с любым из выбранных значений (см. AiTool.toolType). */
    private fun toolTypeSpec(values: List<String>?): Specification<AiTool>? {
        val cleaned = values?.filter { it.isNotBlank() }
        if (cleaned.isNullOrEmpty()) return null
        return Specification { root, _, _ -> root.get<String>("toolType").`in`(cleaned) }
    }

    /** "Содержит хотя бы одно из значений" - для полей-коллекций (roles, framework, constraints). */
    private fun collectionContainsAnySpec(field: String, values: List<String>?): Specification<AiTool>? {
        val cleaned = values?.filter { it.isNotBlank() }
        if (cleaned.isNullOrEmpty()) return null
        return Specification { root, query, _ ->
            query?.distinct(true)
            root.join<AiTool, String>(field).`in`(cleaned)
        }
    }

    private fun searchSpec(search: String?): Specification<AiTool>? =
        if (search.isNullOrBlank()) null else Specification { root, _, cb ->
            val like = "%${search.trim().lowercase()}%"
            cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("ownerName")), like)
            )
        }

    private fun AiTool.toResponse(principal: UserPrincipal?, topIds: Set<UUID>): ToolResponse {
        val canManage = principal != null && (principal.role == "ADMIN" || this.createdBy?.id == principal.id)
        val myRating = principal?.let {
            toolRatingRepository.findByToolIdAndUserId(this.id!!, it.id)?.value
        }
        // Заметки - внутренняя переписка администраторов, обычным пользователям даже количество
        // заметок видно не должно быть - поэтому считаем только для ADMIN.
        val notesCount = if (principal?.role == "ADMIN") toolNoteRepository.countByToolId(this.id!!) else 0L
        return ToolResponse(
            id = this.id!!,
            name = this.name,
            description = this.description,
            shortDescription = this.shortDescription,
            stage = this.stage.name,
            status = this.status.name,
            roles = this.roles.toList().sorted(),
            framework = this.framework.toList().sorted(),
            toolType = this.toolType,
            constraints = this.constraints.toList().sorted(),
            sourceLabel = this.sourceLabel,
            ownerName = this.ownerName,
            downloads = this.downloads,
            dau = this.dau,
            efficiencyPct = this.efficiencyPct,
            isTop = topIds.contains(this.id),
            views = this.views,
            avgRating = if (this.ratingsCount > 0) this.ratingSum.toDouble() / this.ratingsCount else 0.0,
            ratingsCount = this.ratingsCount,
            myRating = myRating,
            canManage = canManage,
            rejectionReason = this.rejectionReason,
            notesCount = notesCount,
            // Как и заметки - видно только администратору (см. AiTool.segment).
            segment = if (principal?.role == "ADMIN") this.segment else null,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
