package ru.heatmap.registry.service

import ru.heatmap.registry.domain.*
import ru.heatmap.registry.dto.*
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.ToolDownloadRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.repository.ToolRatingRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.BadRequestException
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ToolService(
    private val aiToolRepository: AiToolRepository,
    private val appUserRepository: AppUserRepository,
    private val toolRatingRepository: ToolRatingRepository,
    private val toolDownloadRepository: ToolDownloadRepository,
    private val toolNoteRepository: ToolNoteRepository,
    private val notificationService: NotificationService
) {

    /** Вкладки реестра: TOP - витрина лучших решений, остальные - этапы PDLC. */
    fun findByTab(
        tab: String,
        roles: List<String>?,
        framework: String?,
        segments: List<String>?,
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
            equalsSpec("framework", framework),
            collectionContainsAnySpec("segments", segments),
            searchSpec(search)
        ).fold(Specification.where<AiTool>(null)) { acc, next -> acc.and(next) }
        return aiToolRepository.findAll(spec, resolveSort(sort)).map { it.toResponse(principal, topIds) }
    }

    /**
     * Плашка "Топ" больше не выставляется вручную - она автоматически присваивается
     * не более чем 1% опубликованных инструментов с лучшим сочетанием оценки пользователей,
     * просмотров и скачиваний (при равенстве прочего оценка весит больше всего).
     */
    private fun computeTopIds(): Set<Long> {
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
        val avgEfficiency = if (all.isEmpty()) 0 else all.sumOf { it.efficiencyPct } / all.size
        return StatsResponse(
            totalTools = all.size.toLong(),
            newThisWeek = newThisWeek.toLong(),
            accessCount = all.count { it.stage == ToolStage.ACCESS }.toLong(),
            usageCount = all.count { it.stage == ToolStage.USAGE }.toLong(),
            standardCount = all.count { it.stage == ToolStage.STANDARD }.toLong(),
            avgEfficiency = avgEfficiency
        )
    }

    fun filterOptions(): FilterOptionsResponse {
        val all = aiToolRepository.findAll()
        return FilterOptionsResponse(
            roles = all.flatMap { it.roles }.distinct().sorted(),
            frameworks = all.map { it.framework }.distinct().sorted(),
            segments = all.flatMap { it.segments }.distinct().sorted()
        )
    }

    fun findById(id: Long, principal: UserPrincipal?): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        return tool.toResponse(principal, computeTopIds())
    }

    @Transactional
    fun create(request: CreateToolRequest, principal: UserPrincipal): ToolResponse {
        val owner = appUserRepository.findByIdOrNull(principal.id) ?: throw NotFoundException("Пользователь не найден")
        val tool = AiTool(
            name = request.name.trim(),
            description = request.description.trim(),
            stage = ToolStage.ACCESS,
            status = ToolStatus.PENDING,
            roles = request.roles.toMutableSet(),
            framework = request.framework,
            segments = request.segments.toMutableSet(),
            sourceLabel = request.sourceLabel,
            ownerName = owner.fullName,
            createdBy = owner
        )
        // Новый инструмент всегда PENDING - в число опубликованных топ-инструментов попасть не может.
        val saved = aiToolRepository.save(tool)
        notificationService.notifyAdminsOfNewSubmission(saved)
        return saved.toResponse(principal, emptySet())
    }

    @Transactional
    fun update(id: Long, request: UpdateToolRequest, principal: UserPrincipal): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        val isAdmin = principal.role == "ADMIN"
        val isOwner = tool.createdBy?.id == principal.id
        if (!isAdmin && !isOwner) {
            throw ForbiddenException("Недостаточно прав для редактирования этого инструмента")
        }
        // Автор может редактировать инструмент в любом статусе (PENDING/PUBLISHED/REJECTED) -
        // правки уже опубликованного или отклонённого инструмента отправляют его на повторную
        // модерацию (см. ниже), поэтому запрещать редактирование по статусу больше не нужно.
        val wasPublishedOrRejected = tool.status == ToolStatus.PUBLISHED || tool.status == ToolStatus.REJECTED

        request.name?.let { tool.name = it.trim() }
        request.description?.let { tool.description = it.trim() }
        request.roles?.let { tool.roles = it.toMutableSet() }
        request.framework?.let { tool.framework = it }
        request.segments?.let { tool.segments = it.toMutableSet() }
        request.sourceLabel?.let { tool.sourceLabel = it }

        if (isAdmin) {
            request.stage?.let { tool.stage = parseEnum<ToolStage>(it, "этап") }
            request.status?.let { tool.status = parseEnum<ToolStatus>(it, "статус") }
            request.downloads?.let { tool.downloads = it }
            request.dau?.let { tool.dau = it }
            request.efficiencyPct?.let { tool.efficiencyPct = it }
            // "Топ" больше нельзя выставить вручную - плашка присваивается автоматически (см. computeTopIds).
        } else if (wasPublishedOrRejected) {
            // Автор отредактировал уже опубликованный или отклонённый инструмент -
            // отправляем его на повторную модерацию и сбрасываем прежнюю причину отклонения.
            tool.status = ToolStatus.PENDING
            tool.rejectionReason = null
            notificationService.notifyAdminsOfNewSubmission(tool)
        }
        tool.updatedAt = Instant.now()
        return aiToolRepository.save(tool).toResponse(principal, computeTopIds())
    }

    @Transactional
    fun delete(id: Long, principal: UserPrincipal) {
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
    fun incrementView(id: Long, principal: UserPrincipal?): ToolResponse {
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
    fun incrementDownload(id: Long, principal: UserPrincipal?): ToolResponse {
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
    fun rate(id: Long, value: Int, principal: UserPrincipal): ToolResponse {
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
            cb.equal(root.get<Any>("createdBy").get<Long>("id"), principal.id)
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
    fun approve(id: Long): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.PUBLISHED
        tool.rejectionReason = null
        tool.updatedAt = Instant.now()
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = true, reason = null)
        return saved.toResponse(null, computeTopIds())
    }

    @Transactional
    fun reject(id: Long, reason: String): ToolResponse {
        val tool = aiToolRepository.findByIdOrNull(id) ?: throw NotFoundException("Инструмент не найден")
        tool.status = ToolStatus.REJECTED
        tool.rejectionReason = reason.trim()
        tool.updatedAt = Instant.now()
        // Отклонённый инструмент не может быть "Топ".
        val saved = aiToolRepository.save(tool)
        notificationService.notifyOwnerOfModerationResult(saved, approved = false, reason = tool.rejectionReason)
        return saved.toResponse(null, emptySet())
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fieldLabel: String): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw BadRequestException("Некорректное значение поля \"$fieldLabel\": $value") }

    private fun statusSpec(status: ToolStatus): Specification<AiTool> =
        Specification { root, _, cb -> cb.equal(root.get<ToolStatus>("status"), status) }

    private fun tabSpec(tab: String, topIds: Set<Long>): Specification<AiTool>? =
        when (tab.uppercase()) {
            "TOP" -> Specification { root, _, _ -> root.get<Long>("id").`in`(topIds) }
            "ACCESS" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.ACCESS) }
            "USAGE" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.USAGE) }
            "HABIT" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.HABIT) }
            "STANDARD" -> Specification { root, _, cb -> cb.equal(root.get<ToolStage>("stage"), ToolStage.STANDARD) }
            else -> null
        }

    private fun equalsSpec(field: String, value: String?): Specification<AiTool>? =
        if (value.isNullOrBlank()) null else Specification { root, _, cb -> cb.equal(root.get<String>(field), value) }

    /** "Содержит хотя бы одно из значений" - для полей role/segment, которые теперь коллекции. */
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

    private fun AiTool.toResponse(principal: UserPrincipal?, topIds: Set<Long>): ToolResponse {
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
            stage = this.stage.name,
            status = this.status.name,
            roles = this.roles.toList().sorted(),
            framework = this.framework,
            segments = this.segments.toList().sorted(),
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
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
