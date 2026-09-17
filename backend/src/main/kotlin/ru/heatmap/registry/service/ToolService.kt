package ru.heatmap.registry.service

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.ToolDownload
import ru.heatmap.registry.domain.ToolRating
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.CreateToolRequest
import ru.heatmap.registry.dto.FilterOptionsResponse
import ru.heatmap.registry.dto.StatsResponse
import ru.heatmap.registry.dto.ToolCountsResponse
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.dto.UpdateToolRequest
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.PresetConstraintRepository
import ru.heatmap.registry.repository.PresetRoleRepository
import ru.heatmap.registry.repository.ToolDownloadRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.specification.collectionContainsAnySpec
import ru.heatmap.registry.specification.searchSpec
import ru.heatmap.registry.specification.statusSpec
import ru.heatmap.registry.specification.tabSpec
import ru.heatmap.registry.specification.toolTypeSpec
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.DuplicateSourceLabelException
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException

// "Ссылка на инструмент" должна быть уникальна, но только среди активных карточек - отклонённые
// и архивные не учитываются (см. AiToolRepository.findFirstBySourceLabelAndStatusIn), чтобы ту же
// ссылку можно было завести заново в новой заявке после отклонения/архивации прежней.
private val ACTIVE_SOURCE_LABEL_STATUSES = listOf(ToolStatus.PENDING, ToolStatus.PUBLISHED)

// Базовый набор категорий "Тип инструмента" (см. AiTool.toolType) - всегда виден в форме/фильтре
// (см. ToolService.filterOptions), но больше не единственно допустимый: администратор может
// завести свой вариант, как и с ролями/фреймворком/ограничениями, поэтому строгой валидации
// по regexp для этого поля нет - только ограничение длины (см. registry-api.yaml: toolType).
val TOOL_TYPE_OPTIONS = listOf("Skill", "MCP", "Agent", "Harness", "Tool", "Framework", "Другое")

// Базовый набор "Агентский фреймворк" (см. AiTool.framework) - раньше не было вовсе (см.
// ToolService.filterOptions): на пустой базе, без демо-инструментов, поле в форме оставалось
// без единого варианта выбора. Как и с типом инструмента - не единственно допустимый набор,
// администратор может ввести свой вариант.
val FRAMEWORK_OPTIONS = listOf("Openspec", "Superpowers", "SDD не применим")

@Service
class ToolService(
    private val aiToolRepository: AiToolRepository,
    private val appUserRepository: AppUserRepository,
    private val toolRatingRepository: ToolRatingRepository,
    private val toolDownloadRepository: ToolDownloadRepository,
    private val presetRoleRepository: PresetRoleRepository,
    private val presetConstraintRepository: PresetConstraintRepository,
    private val notificationService: NotificationService,
    private val toolResponseAssembler: AiToolResponseAssembler
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
        val topIds = toolResponseAssembler.computeTopIds()
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
        ).fold(Specification.unrestricted<AiTool>()) { acc, next -> acc.and(next) }
        return aiToolRepository.findAll(spec, resolveSort(sort))
            .map { toolResponseAssembler.toResponse(it, principal, topIds) }
    }

    fun counts(): ToolCountsResponse {
        val published = ToolStatus.PUBLISHED
        return ToolCountsResponse(
            top = toolResponseAssembler.computeTopIds().size.toLong(),
            access = aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, published),
            usage = aiToolRepository.countByStageAndStatus(ToolStage.USAGE, published),
            habit = aiToolRepository.countByStageAndStatus(ToolStage.HABIT, published),
            standard = aiToolRepository.countByStageAndStatus(ToolStage.STANDARD, published),
            total = aiToolRepository.countByStatus(published)
        )
    }

    // Раньше грузило все опубликованные инструменты и считало через .count{} в памяти - теперь
    // те же цифры, что и в counts(), берутся агрегатами прямо из БД.
    fun stats(): StatsResponse {
        val published = ToolStatus.PUBLISHED
        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        return StatsResponse(
            totalTools = aiToolRepository.countByStatus(published),
            newThisWeek = aiToolRepository.countByStatusAndCreatedAtAfter(published, weekAgo),
            accessCount = aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, published),
            usageCount = aiToolRepository.countByStageAndStatus(ToolStage.USAGE, published),
            standardCount = aiToolRepository.countByStageAndStatus(ToolStage.STANDARD, published)
        )
    }

    // Раньше грузило все инструменты целиком (с eager-коллекциями roles/framework/constraints)
    // только чтобы собрать distinct-строки - теперь эти списки выбираются прямо в БД
    // (см. AiToolRepository.findDistinct*), без загрузки самих инструментов.
    fun filterOptions(): FilterOptionsResponse {
        // Базовые наборы (preset_role/preset_constraint - см. 010-create-preset-tables.sql)
        // видны в форме/фильтре всегда, даже если ни один инструмент ещё не использует
        // конкретное значение - плюс любые дополнительные значения, которые уже реально
        // использованы (например, введены администратором как "свой вариант" -
        // см. MultiSelectDropdown.allowCustom).
        val presetRoles = presetRoleRepository.findAll().map { it.role }
        val presetConstraints = presetConstraintRepository.findAll().map { it.value }
        return FilterOptionsResponse(
            roles = (presetRoles + aiToolRepository.findDistinctRoles())
                .filter { it.isNotBlank() }.distinct().sorted(),
            // Базовый набор FRAMEWORK_OPTIONS плюс реально сохранённые значения - как и с типом
            // инструмента, администратор может ввести свой вариант (см. AiTool.framework).
            frameworks = (FRAMEWORK_OPTIONS + aiToolRepository.findDistinctFrameworks())
                .filter { it.isNotBlank() }.distinct().sorted(),
            constraints = (presetConstraints + aiToolRepository.findDistinctConstraints())
                .filter { it.isNotBlank() }.distinct().sorted(),
            // Базовый набор TOOL_TYPE_OPTIONS плюс реально сохранённые значения - включая
            // "свой вариант", который администратор мог ввести вручную (см.
            // AddToolModal/SelectDropdown.allowCustom), как и с ролями/ограничениями выше.
            toolTypes = (TOOL_TYPE_OPTIONS + aiToolRepository.findDistinctToolTypes())
                .filter { it.isNotBlank() }.distinct().sorted()
        )
    }

    fun findById(id: UUID, principal: UserPrincipal?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        return toolResponseAssembler.toResponse(tool, principal, toolResponseAssembler.computeTopIds())
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
            framework = request.framework.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.toMutableSet(),
            toolType = request.toolType?.trim()?.takeIf { it.isNotBlank() },
            constraints = request.constraints.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.toMutableSet(),
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
        return toolResponseAssembler.toResponse(saved, principal, emptySet())
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
        request.framework?.let {
            tool.framework = it.map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableSet()
        }
        request.toolType?.let { tool.toolType = it.trim().takeIf { s -> s.isNotBlank() } }
        request.constraints?.let {
            tool.constraints = it.map { s -> s.trim() }.filter { s -> s.isNotBlank() }.toMutableSet()
        }
        // Ссылка обязательна для обычного пользователя (как и при создании), но необязательна
        // для администратора - пустая строка от него сохраняется как null.
        request.sourceLabel?.let {
            val trimmed = it.trim()
            if (!isAdmin && trimmed.isBlank()) {
                throw BadRequestException("Введите ссылку на инструмент")
            }
            val newSourceLabel = trimmed.takeIf { s -> s.isNotBlank() }
            if (newSourceLabel != null) {
                aiToolRepository.findFirstBySourceLabelAndStatusInAndIdNot(
                    newSourceLabel,
                    ACTIVE_SOURCE_LABEL_STATUSES,
                    tool.id!!
                )
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
        return toolResponseAssembler.toResponse(
            aiToolRepository.save(tool),
            principal,
            toolResponseAssembler.computeTopIds()
        )
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
        return toolResponseAssembler.toResponse(aiToolRepository.save(tool), principal, emptySet())
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
        return toolResponseAssembler.toResponse(
            aiToolRepository.save(tool),
            principal,
            toolResponseAssembler.computeTopIds()
        )
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
            val owner =
                appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
            toolDownloadRepository.save(ToolDownload(tool = tool, user = owner))
            tool.downloads += 1
        }
        return toolResponseAssembler.toResponse(
            aiToolRepository.save(tool),
            principal,
            toolResponseAssembler.computeTopIds()
        )
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
            val owner =
                appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
            toolRatingRepository.save(ToolRating(tool = tool, user = owner, value = value))
            tool.ratingSum += value
            tool.ratingsCount += 1
        }
        return toolResponseAssembler.toResponse(
            aiToolRepository.save(tool),
            principal,
            toolResponseAssembler.computeTopIds()
        )
    }

    /** Инструменты, которые пользователь скачивал - для вкладки "Мои инструменты", где можно оценить/переоценить. */
    @Transactional(readOnly = true)
    fun findDownloaded(principal: UserPrincipal): List<ToolResponse> {
        val topIds = toolResponseAssembler.computeTopIds()
        return toolDownloadRepository.findByUserIdOrderByCreatedAtDesc(principal.id)
            .map { toolResponseAssembler.toResponse(it.tool, principal, topIds) }
    }

    /** Собственные инструменты пользователя, включая те, что ещё на модерации или отклонены. */
    fun findMine(principal: UserPrincipal): List<ToolResponse> {
        val spec = Specification<AiTool> { root, _, cb ->
            cb.equal(root.get<Any>("createdBy").get<UUID>("id"), principal.id)
        }
        val sort =
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        val topIds = toolResponseAssembler.computeTopIds()
        return aiToolRepository.findAll(spec, sort).map { toolResponseAssembler.toResponse(it, principal, topIds) }
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

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fieldLabel: String): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw BadRequestException("Некорректное значение поля \"$fieldLabel\": $value") }
}
