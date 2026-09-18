package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.PresetConstraint
import ru.heatmap.registry.domain.PresetRole
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.domain.ToolDownload
import ru.heatmap.registry.domain.ToolRating
import ru.heatmap.registry.domain.ToolStage
import ru.heatmap.registry.domain.ToolStatus
import ru.heatmap.registry.dto.CreateToolRequest
import ru.heatmap.registry.dto.ToolResponse
import ru.heatmap.registry.dto.UpdateToolRequest
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
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ToolServiceTest {

    private val aiToolRepository = mock<AiToolRepository>()
    private val appUserRepository = mock<AppUserRepository>()
    private val toolRatingRepository = mock<ToolRatingRepository>()
    private val toolDownloadRepository = mock<ToolDownloadRepository>()
    private val toolNoteRepository = mock<ToolNoteRepository>()
    private val presetRoleRepository = mock<PresetRoleRepository>()
    private val presetConstraintRepository = mock<PresetConstraintRepository>()
    private val notificationService = mock<NotificationService>()
    private val toolResponseAssembler = mock<AiToolResponseAssembler>()

    private val service = ToolService(
        aiToolRepository,
        appUserRepository,
        toolRatingRepository,
        toolDownloadRepository,
        toolNoteRepository,
        presetRoleRepository,
        presetConstraintRepository,
        notificationService,
        toolResponseAssembler
    )

    private fun user(id: UUID = UUID.randomUUID(), role: Role = Role.USER, fullName: String = "Full Name") = AppUser(
        id = id,
        username = "u-$id",
        passwordHash = "h",
        fullName = fullName,
        role = role
    )

    private fun tool(
        id: UUID = UUID.randomUUID(),
        createdBy: AppUser? = null,
        status: ToolStatus = ToolStatus.PUBLISHED,
        sourceLabel: String? = null
    ) = AiTool(
        id = id,
        name = "Tool",
        description = "Desc",
        ownerName = "Owner",
        status = status,
        createdBy = createdBy,
        sourceLabel = sourceLabel
    )

    private fun fakeResponse(id: UUID = UUID.randomUUID()) = ToolResponse(
        id = id,
        name = "Tool",
        description = "Desc",
        stage = "ACCESS",
        status = "PUBLISHED",
        roles = emptyList(),
        framework = emptyList(),
        constraints = emptyList(),
        ownerName = "Owner",
        downloads = 0,
        efficiencyPct = 0,
        isTop = false,
        views = 0,
        avgRating = 0.0,
        ratingsCount = 0,
        canManage = false,
        notesCount = 0L,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun stubAssembler() {
        whenever(toolResponseAssembler.computeTopIds()).thenReturn(emptySet())
        // principal - UserPrincipal? - вызывается и с null (см. findById/incrementView/incrementDownload
        // для анонимов): any() у mockito-kotlin матчит только ненулевые аргументы, поэтому для
        // этого параметра нужен именно anyOrNull(), иначе немокнутый вызов возвращает null и падает NPE.
        whenever(toolResponseAssembler.toResponse(any(), anyOrNull(), any())).thenAnswer { invocation ->
            fakeResponse((invocation.arguments[0] as AiTool).id!!)
        }
    }

    @Nested
    inner class Counts {
        @Test
        fun `assembles counts from repository and assembler`() {
            whenever(toolResponseAssembler.computeTopIds()).thenReturn(setOf(UUID.randomUUID(), UUID.randomUUID()))
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, ToolStatus.PUBLISHED)).thenReturn(1L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.USAGE, ToolStatus.PUBLISHED)).thenReturn(2L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.HABIT, ToolStatus.PUBLISHED)).thenReturn(3L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.STANDARD, ToolStatus.PUBLISHED)).thenReturn(4L)
            whenever(aiToolRepository.countByStatus(ToolStatus.PUBLISHED)).thenReturn(10L)

            val result = service.counts()

            assertThat(result.top).isEqualTo(2L)
            assertThat(result.access).isEqualTo(1L)
            assertThat(result.usage).isEqualTo(2L)
            assertThat(result.habit).isEqualTo(3L)
            assertThat(result.standard).isEqualTo(4L)
            assertThat(result.total).isEqualTo(10L)
        }
    }

    @Nested
    inner class Stats {
        @Test
        fun `assembles stats from repository aggregates`() {
            whenever(aiToolRepository.countByStatus(ToolStatus.PUBLISHED)).thenReturn(50L)
            whenever(aiToolRepository.countByStatusAndCreatedAtAfter(eq(ToolStatus.PUBLISHED), any())).thenReturn(5L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.ACCESS, ToolStatus.PUBLISHED)).thenReturn(6L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.USAGE, ToolStatus.PUBLISHED)).thenReturn(7L)
            whenever(aiToolRepository.countByStageAndStatus(ToolStage.STANDARD, ToolStatus.PUBLISHED)).thenReturn(8L)

            val result = service.stats()

            assertThat(result.totalTools).isEqualTo(50L)
            assertThat(result.newThisWeek).isEqualTo(5L)
            assertThat(result.accessCount).isEqualTo(6L)
            assertThat(result.usageCount).isEqualTo(7L)
            assertThat(result.standardCount).isEqualTo(8L)
        }
    }

    @Nested
    inner class FilterOptions {
        @Test
        fun `merges presets with distinct db values, filters blanks, dedups and sorts`() {
            whenever(presetRoleRepository.findAll()).thenReturn(listOf(PresetRole("Аналитика"), PresetRole("Разработка")))
            whenever(aiToolRepository.findDistinctRoles()).thenReturn(listOf("разработка", " ", "Дизайн"))

            whenever(aiToolRepository.findDistinctFrameworks()).thenReturn(listOf("Openspec", "CustomFW", ""))

            whenever(presetConstraintRepository.findAll()).thenReturn(listOf(PresetConstraint("BPM"), PresetConstraint("ПКАП")))
            whenever(aiToolRepository.findDistinctConstraints()).thenReturn(listOf("bpm", ""))

            whenever(aiToolRepository.findDistinctToolTypes()).thenReturn(listOf("CustomType", ""))

            val result = service.filterOptions()

            assertThat(result.roles).containsExactly("Аналитика", "Дизайн", "Разработка", "разработка")
            assertThat(result.frameworks).containsExactly("CustomFW", "Openspec", "SDD не применим", "Superpowers")
            assertThat(result.constraints).containsExactly("BPM", "bpm", "ПКАП")
            assertThat(result.toolTypes)
                .containsExactly("Agent", "CustomType", "Framework", "Harness", "MCP", "Skill", "Tool", "Другое")
        }
    }

    @Nested
    inner class FindById {
        @Test
        fun `returns response for existing tool`() {
            val t = tool()
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            stubAssembler()

            val result = service.findById(t.id!!, null)

            assertThat(result.id).isEqualTo(t.id)
        }

        @Test
        fun `throws NotFound for missing tool`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.findById(id, null) }.isInstanceOf(NotFoundException::class.java)
        }
    }

    @Nested
    inner class Create {
        private fun request(
            sourceLabel: String? = "https://example.com/tool",
            stage: String? = null,
            status: String? = null
        ) = CreateToolRequest(
            name = "New tool",
            description = "Description",
            roles = listOf("Разработка"),
            shortDescription = null,
            framework = null,
            toolType = null,
            constraints = null,
            sourceLabel = sourceLabel,
            stage = stage,
            status = status
        )

        @Test
        fun `non-admin without sourceLabel throws BadRequest`() {
            val owner = user(role = Role.USER)
            whenever(appUserRepository.findById(owner.id!!)).thenReturn(Optional.of(owner))
            val principal = UserPrincipal(owner)

            assertThatThrownBy { service.create(request(sourceLabel = null), principal) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `duplicate active sourceLabel throws DuplicateSourceLabelException`() {
            val owner = user(role = Role.USER)
            whenever(appUserRepository.findById(owner.id!!)).thenReturn(Optional.of(owner))
            val existing = tool(status = ToolStatus.PUBLISHED, sourceLabel = "https://example.com/tool")
            whenever(
                aiToolRepository.findFirstBySourceLabelAndStatusIn(
                    "https://example.com/tool",
                    listOf(ToolStatus.PENDING, ToolStatus.PUBLISHED)
                )
            ).thenReturn(existing)
            val principal = UserPrincipal(owner)

            assertThatThrownBy { service.create(request(), principal) }
                .isInstanceOf(DuplicateSourceLabelException::class.java)
                .satisfies({ ex ->
                    ex as DuplicateSourceLabelException
                    assertThat(ex.toolId).isEqualTo(existing.id)
                    assertThat(ex.toolName).isEqualTo(existing.name)
                })
        }

        @Test
        fun `non-admin always creates as ACCESS PENDING and notifies admins`() {
            val owner = user(role = Role.USER)
            whenever(appUserRepository.findById(owner.id!!)).thenReturn(Optional.of(owner))
            whenever(aiToolRepository.findFirstBySourceLabelAndStatusIn(any(), any())).thenReturn(null)
            val saved = argumentCaptor<AiTool>()
            // create() строит новый AiTool без id (его проставляет JPA при реальном save) -
            // stubAssembler() ниже требует ненулевой id у сохранённого инструмента.
            whenever(aiToolRepository.save(saved.capture())).thenAnswer {
                (it.arguments[0] as AiTool).also { t -> if (t.id == null) t.id = UUID.randomUUID() }
            }
            stubAssembler()
            val principal = UserPrincipal(owner)

            service.create(request(stage = "STANDARD", status = "PUBLISHED"), principal)

            assertThat(saved.firstValue.stage).isEqualTo(ToolStage.ACCESS)
            assertThat(saved.firstValue.status).isEqualTo(ToolStatus.PENDING)
            verify(notificationService).notifyAdminsOfNewSubmission(any())
        }

        @Test
        fun `admin can set stage and status explicitly, case-insensitively`() {
            val admin = user(role = Role.ADMIN)
            whenever(appUserRepository.findById(admin.id!!)).thenReturn(Optional.of(admin))
            whenever(aiToolRepository.findFirstBySourceLabelAndStatusIn(any(), any())).thenReturn(null)
            val saved = argumentCaptor<AiTool>()
            whenever(aiToolRepository.save(saved.capture())).thenAnswer {
                (it.arguments[0] as AiTool).also { t -> if (t.id == null) t.id = UUID.randomUUID() }
            }
            stubAssembler()
            val principal = UserPrincipal(admin)

            service.create(request(stage = "usage", status = "published"), principal)

            assertThat(saved.firstValue.stage).isEqualTo(ToolStage.USAGE)
            assertThat(saved.firstValue.status).isEqualTo(ToolStatus.PUBLISHED)
            verify(notificationService, never()).notifyAdminsOfNewSubmission(any())
        }

        @Test
        fun `admin with disallowed status throws BadRequest`() {
            val admin = user(role = Role.ADMIN)
            whenever(appUserRepository.findById(admin.id!!)).thenReturn(Optional.of(admin))
            whenever(aiToolRepository.findFirstBySourceLabelAndStatusIn(any(), any())).thenReturn(null)
            val principal = UserPrincipal(admin)

            assertThatThrownBy { service.create(request(status = "archived"), principal) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `admin with garbage stage throws BadRequest`() {
            val admin = user(role = Role.ADMIN)
            whenever(appUserRepository.findById(admin.id!!)).thenReturn(Optional.of(admin))
            whenever(aiToolRepository.findFirstBySourceLabelAndStatusIn(any(), any())).thenReturn(null)
            val principal = UserPrincipal(admin)

            assertThatThrownBy { service.create(request(stage = "not-a-stage"), principal) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `missing owner throws NotFound`() {
            val owner = user()
            whenever(appUserRepository.findById(owner.id!!)).thenReturn(Optional.empty())
            val principal = UserPrincipal(owner)

            assertThatThrownBy { service.create(request(), principal) }.isInstanceOf(NotFoundException::class.java)
        }
    }

    @Nested
    inner class Update {
        private fun request(
            sourceLabel: String? = null,
            stage: String? = null,
            status: String? = null,
            segment: String? = null
        ) = UpdateToolRequest(
            name = null,
            description = null,
            shortDescription = null,
            roles = null,
            framework = null,
            toolType = null,
            constraints = null,
            sourceLabel = sourceLabel,
            stage = stage,
            status = status,
            downloads = null,
            dau = null,
            efficiencyPct = null,
            segment = segment
        )

        @Test
        fun `missing tool throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(aiToolRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.update(id, request(), UserPrincipal(user())) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `non-owner non-admin throws Forbidden`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            val stranger = UserPrincipal(user())

            assertThatThrownBy { service.update(t.id!!, request(), stranger) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        private fun ownerResubmitCase(startStatus: ToolStatus) {
            val owner = user()
            val t = tool(createdBy = owner, status = startStatus)
            t.rejectionReason = "old reason"
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()
            val principal = UserPrincipal(owner)

            service.update(t.id!!, request(), principal)

            assertThat(t.status).isEqualTo(ToolStatus.PENDING)
            assertThat(t.rejectionReason).isNull()
            verify(notificationService).notifyAdminsOfNewSubmission(t)
        }

        @Test
        fun `owner editing a PUBLISHED tool resubmits to PENDING`() = ownerResubmitCase(ToolStatus.PUBLISHED)

        @Test
        fun `owner editing a REJECTED tool resubmits to PENDING`() = ownerResubmitCase(ToolStatus.REJECTED)

        @Test
        fun `owner editing a DRAFT tool resubmits to PENDING`() = ownerResubmitCase(ToolStatus.DRAFT)

        @Test
        fun `owner editing a PENDING tool does not resubmit or notify`() {
            val owner = user()
            val t = tool(createdBy = owner, status = ToolStatus.PENDING)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.update(t.id!!, request(), UserPrincipal(owner))

            assertThat(t.status).isEqualTo(ToolStatus.PENDING)
            verify(notificationService, never()).notifyAdminsOfNewSubmission(any())
        }

        @Test
        fun `admin can directly set fields and skips resubmit logic even on PUBLISHED`() {
            val owner = user()
            val t = tool(createdBy = owner, status = ToolStatus.PUBLISHED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()
            val admin = UserPrincipal(user(role = Role.ADMIN))

            service.update(t.id!!, request(stage = "HABIT", status = "ARCHIVED", segment = "Сегмент А"), admin)

            assertThat(t.stage).isEqualTo(ToolStage.HABIT)
            assertThat(t.status).isEqualTo(ToolStatus.ARCHIVED)
            assertThat(t.segment).isEqualTo("Сегмент А")
            verify(notificationService, never()).notifyAdminsOfNewSubmission(any())
        }

        @Test
        fun `non-admin blank sourceLabel throws BadRequest`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.update(t.id!!, request(sourceLabel = "   "), UserPrincipal(owner)) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `sourceLabel collision excluding self throws DuplicateSourceLabelException`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            val other = tool()
            whenever(
                aiToolRepository.findFirstBySourceLabelAndStatusInAndIdNot(
                    "https://example.com/dup",
                    listOf(ToolStatus.PENDING, ToolStatus.PUBLISHED),
                    t.id!!
                )
            ).thenReturn(other)

            assertThatThrownBy {
                service.update(t.id!!, request(sourceLabel = "https://example.com/dup"), UserPrincipal(owner))
            }.isInstanceOf(DuplicateSourceLabelException::class.java)
        }
    }

    @Nested
    inner class Withdraw {
        @Test
        fun `non-owner throws Forbidden`() {
            val owner = user()
            val t = tool(createdBy = owner, status = ToolStatus.PENDING)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.withdraw(t.id!!, UserPrincipal(user())) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        fun `non-pending status throws BadRequest`() {
            val owner = user()
            val t = tool(createdBy = owner, status = ToolStatus.PUBLISHED)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.withdraw(t.id!!, UserPrincipal(owner)) }
                .isInstanceOf(BadRequestException::class.java)
        }

        @Test
        fun `owner withdrawing a pending tool moves it to DRAFT`() {
            val owner = user()
            val t = tool(createdBy = owner, status = ToolStatus.PENDING)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.save(any())).thenAnswer { it.arguments[0] }
            stubAssembler()

            service.withdraw(t.id!!, UserPrincipal(owner))

            assertThat(t.status).isEqualTo(ToolStatus.DRAFT)
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `non-owner non-admin throws Forbidden`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            assertThatThrownBy { service.delete(t.id!!, UserPrincipal(user())) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        fun `owner delete removes only the tool - dependent rows are cleaned up by DB-level ON DELETE CASCADE`() {
            // Раньше ToolService.delete() вручную чистил tool_rating/tool_download/tool_note
            // перед удалением ai_tool - но окно между ручной очисткой и самим удалением давало
            // гонку с параллельным rate/download от пользователя (проверено эмпирически: 500
            // Referential integrity constraint violation). Теперь все три FK настроены с
            // ON DELETE CASCADE (см. 011-cascade-delete-tool-rating-download.sql), поэтому
            // сервис только удаляет саму строку ai_tool - и никогда не должен трогать
            // toolRatingRepository/toolDownloadRepository/toolNoteRepository напрямую.
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            service.delete(t.id!!, UserPrincipal(owner))

            verify(aiToolRepository).delete(t)
            verifyNoInteractions(toolRatingRepository, toolDownloadRepository, toolNoteRepository)
        }

        @Test
        fun `admin can delete any tool`() {
            val owner = user()
            val t = tool(createdBy = owner)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))

            service.delete(t.id!!, UserPrincipal(user(role = Role.ADMIN)))

            verify(aiToolRepository).delete(t)
        }
    }

    @Nested
    inner class IncrementView {
        @Test
        fun `increments views by one atomically`() {
            val t = tool()
            t.views = 5
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            // Атомарный UPDATE ... SET views = views + 1 (см. AiToolRepository.incrementViews) -
            // в проде выполняется на стороне БД одним запросом; здесь симулируем его эффект,
            // чтобы не потерять покрытие теста на конкурентно-безопасную реализацию.
            whenever(aiToolRepository.incrementViews(t.id!!)).thenAnswer { t.views += 1; 1 }
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            stubAssembler()

            service.incrementView(t.id!!, null)

            assertThat(t.views).isEqualTo(6)
            verify(aiToolRepository).incrementViews(t.id!!)
            verify(aiToolRepository, never()).save(any())
        }
    }

    @Nested
    inner class IncrementDownload {
        @Test
        fun `anonymous always increments without tracking`() {
            val t = tool()
            t.downloads = 3
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(aiToolRepository.incrementDownloads(t.id!!)).thenAnswer { t.downloads += 1; 1 }
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            stubAssembler()

            service.incrementDownload(t.id!!, null)

            assertThat(t.downloads).isEqualTo(4)
            verify(toolDownloadRepository, never()).save(any())
        }

        @Test
        fun `first download by a user is tracked and counted`() {
            val t = tool()
            t.downloads = 0
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.incrementDownloads(t.id!!)).thenAnswer { t.downloads += 1; 1 }
            val owner = user()
            whenever(toolDownloadRepository.existsByToolIdAndUserId(t.id!!, owner.id!!)).thenReturn(false)
            whenever(appUserRepository.findById(owner.id!!)).thenReturn(Optional.of(owner))
            stubAssembler()

            service.incrementDownload(t.id!!, UserPrincipal(owner))

            assertThat(t.downloads).isEqualTo(1)
            verify(toolDownloadRepository).save(any<ToolDownload>())
        }

        @Test
        fun `repeat download by the same user is not counted again`() {
            val t = tool()
            t.downloads = 1
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            val owner = user()
            whenever(toolDownloadRepository.existsByToolIdAndUserId(t.id!!, owner.id!!)).thenReturn(true)
            stubAssembler()

            service.incrementDownload(t.id!!, UserPrincipal(owner))

            assertThat(t.downloads).isEqualTo(1)
            verify(toolDownloadRepository, never()).save(any())
            verify(aiToolRepository, never()).incrementDownloads(any())
        }
    }

    @Nested
    inner class Rate {
        @Test
        fun `new rating is saved and sum plus count updated atomically`() {
            val t = tool()
            t.ratingSum = 0
            t.ratingsCount = 0
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.addNewRating(eq(t.id!!), eq(4L))).thenAnswer {
                t.ratingSum += 4
                t.ratingsCount += 1
                1
            }
            val rater = user()
            whenever(toolRatingRepository.findByToolIdAndUserId(t.id!!, rater.id!!)).thenReturn(null)
            whenever(appUserRepository.findById(rater.id!!)).thenReturn(Optional.of(rater))
            stubAssembler()

            service.rate(t.id!!, 4, UserPrincipal(rater))

            assertThat(t.ratingSum).isEqualTo(4)
            assertThat(t.ratingsCount).isEqualTo(1)
            verify(toolRatingRepository).save(any<ToolRating>())
        }

        @Test
        fun `existing rating is updated in place and sum adjusted by delta`() {
            val t = tool()
            t.ratingSum = 3
            t.ratingsCount = 1
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(aiToolRepository.adjustRatingSum(eq(t.id!!), eq(2L))).thenAnswer { t.ratingSum += 2; 1 }
            val rater = user()
            val existing = ToolRating(tool = t, user = rater, value = 3)
            whenever(toolRatingRepository.findByToolIdAndUserId(t.id!!, rater.id!!)).thenReturn(existing)
            stubAssembler()

            service.rate(t.id!!, 5, UserPrincipal(rater))

            assertThat(t.ratingSum).isEqualTo(5)
            assertThat(t.ratingsCount).isEqualTo(1)
            assertThat(existing.value).isEqualTo(5)
            verify(toolRatingRepository).save(existing)
        }
    }

    @Nested
    inner class FindDownloadedAndMine {
        @Test
        fun `findDownloaded maps a user's downloaded tools`() {
            val owner = user()
            val t = tool()
            whenever(toolDownloadRepository.findByUserIdOrderByCreatedAtDesc(owner.id!!))
                .thenReturn(listOf(ToolDownload(tool = t, user = owner)))
            stubAssembler()

            val result = service.findDownloaded(UserPrincipal(owner))

            assertThat(result).hasSize(1)
        }

        @Test
        fun `findMine maps the caller's own tools`() {
            val owner = user()
            whenever(aiToolRepository.findAll(any<Specification<AiTool>>(), any<Sort>())).thenReturn(listOf(tool(createdBy = owner)))
            stubAssembler()

            val result = service.findMine(UserPrincipal(owner))

            assertThat(result).hasSize(1)
        }
    }

    @Nested
    inner class SortResolution {
        private fun sortFor(sort: String?): Sort {
            val captor = argumentCaptor<Sort>()
            whenever(aiToolRepository.findAll(any<Specification<AiTool>>(), captor.capture())).thenReturn(emptyList())
            stubAssembler()

            service.findByTab("TOP", null, null, null, null, null, sort, null)

            return captor.firstValue
        }

        @Test
        fun `CREATED_AT sorts by createdAt descending`() {
            val order = sortFor("CREATED_AT").getOrderFor("createdAt")
            assertThat(order).isNotNull
            assertThat(order!!.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `UPDATED_AT sorts by updatedAt descending`() {
            val order = sortFor("UPDATED_AT").getOrderFor("updatedAt")
            assertThat(order).isNotNull
            assertThat(order!!.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `VIEWS sorts by views descending`() {
            val order = sortFor("VIEWS").getOrderFor("views")
            assertThat(order).isNotNull
            assertThat(order!!.direction).isEqualTo(Sort.Direction.DESC)
        }

        @Test
        fun `null or unrecognized sort defaults to efficiencyPct then downloads descending`() {
            for (value in listOf(null, "garbage")) {
                val sort = sortFor(value)
                assertThat(sort.getOrderFor("efficiencyPct")?.direction).isEqualTo(Sort.Direction.DESC)
                assertThat(sort.getOrderFor("downloads")?.direction).isEqualTo(Sort.Direction.DESC)
            }
        }
    }
}
