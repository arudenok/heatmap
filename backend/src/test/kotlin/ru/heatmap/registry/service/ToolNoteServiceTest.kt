package ru.heatmap.registry.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.domain.ToolNote
import ru.heatmap.registry.dto.CreateToolNoteRequest
import ru.heatmap.registry.dto.ToolNoteResponse
import ru.heatmap.registry.dto.UpdateToolNoteRequest
import ru.heatmap.registry.mapper.ToolNoteMapper
import ru.heatmap.registry.repository.AiToolRepository
import ru.heatmap.registry.repository.AppUserRepository
import ru.heatmap.registry.repository.ToolNoteRepository
import ru.heatmap.registry.security.UserPrincipal
import ru.heatmap.registry.web.ForbiddenException
import ru.heatmap.registry.web.NotFoundException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ToolNoteServiceTest {

    private val toolNoteRepository = mock<ToolNoteRepository>()
    private val aiToolRepository = mock<AiToolRepository>()
    private val appUserRepository = mock<AppUserRepository>()
    private val notificationService = mock<NotificationService>()
    private val toolNoteMapper = mock<ToolNoteMapper>()

    private val service = ToolNoteService(toolNoteRepository, aiToolRepository, appUserRepository, notificationService, toolNoteMapper)

    private fun user(id: UUID = UUID.randomUUID()) = AppUser(id = id, username = "u-$id", passwordHash = "h", fullName = "Full Name", role = Role.ADMIN)
    private fun tool(id: UUID = UUID.randomUUID()) = AiTool(id = id, name = "Tool", description = "Desc", ownerName = "Owner")

    private fun baseResponse(note: ToolNote) = ToolNoteResponse(
        id = note.id ?: UUID.randomUUID(),
        toolId = note.tool.id!!,
        authorId = note.author.id!!,
        authorName = note.author.fullName,
        text = note.text,
        canManage = false,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt
    )

    @Nested
    inner class ListByTool {
        @Test
        fun `tool not found throws NotFound`() {
            val toolId = UUID.randomUUID()
            whenever(aiToolRepository.existsById(toolId)).thenReturn(false)

            assertThatThrownBy { service.listByTool(toolId, UserPrincipal(user())) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `canManage is true only for notes authored by the caller`() {
            val t = tool()
            val me = user()
            val other = user()
            val myNote = ToolNote(tool = t, author = me, text = "mine")
            val otherNote = ToolNote(tool = t, author = other, text = "theirs")
            whenever(aiToolRepository.existsById(t.id!!)).thenReturn(true)
            whenever(toolNoteRepository.findByToolIdOrderByCreatedAtAsc(t.id!!)).thenReturn(listOf(myNote, otherNote))
            whenever(toolNoteMapper.toBaseResponse(any())).thenAnswer { baseResponse(it.arguments[0] as ToolNote) }

            val result = service.listByTool(t.id!!, UserPrincipal(me))

            assertThat(result).hasSize(2)
            assertThat(result[0].canManage).isTrue()
            assertThat(result[1].canManage).isFalse()
        }
    }

    @Nested
    inner class Create {
        @Test
        fun `tool not found throws NotFound`() {
            val toolId = UUID.randomUUID()
            whenever(aiToolRepository.findById(toolId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                service.create(toolId, CreateToolNoteRequest("text"), UserPrincipal(user()))
            }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `author not found throws NotFound`() {
            val t = tool()
            val me = user()
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(appUserRepository.findById(me.id!!)).thenReturn(Optional.empty())

            assertThatThrownBy {
                service.create(t.id!!, CreateToolNoteRequest("text"), UserPrincipal(me))
            }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `saves note with equal created and updated timestamps, notifies admins, and is self-manageable`() {
            val t = tool()
            val me = user()
            whenever(aiToolRepository.findById(t.id!!)).thenReturn(Optional.of(t))
            whenever(appUserRepository.findById(me.id!!)).thenReturn(Optional.of(me))
            whenever(toolNoteRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(toolNoteMapper.toBaseResponse(any())).thenAnswer { baseResponse(it.arguments[0] as ToolNote) }

            val response = service.create(t.id!!, CreateToolNoteRequest("  Заметка  "), UserPrincipal(me))

            assertThat(response.canManage).isTrue()
            verify(notificationService).notifyAdminsOfNewNote(t, me.id!!)
            val captor = org.mockito.kotlin.argumentCaptor<ToolNote>()
            verify(toolNoteRepository).save(captor.capture())
            assertThat(captor.firstValue.text).isEqualTo("Заметка")
            assertThat(captor.firstValue.createdAt).isEqualTo(captor.firstValue.updatedAt)
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `note not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(toolNoteRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy {
                service.update(id, UpdateToolNoteRequest("text"), UserPrincipal(user()))
            }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `wrong author throws Forbidden`() {
            val t = tool()
            val author = user()
            val note = ToolNote(id = UUID.randomUUID(), tool = t, author = author, text = "old")
            whenever(toolNoteRepository.findById(note.id!!)).thenReturn(Optional.of(note))

            assertThatThrownBy {
                service.update(note.id!!, UpdateToolNoteRequest("new text"), UserPrincipal(user()))
            }.isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        fun `author can update text and updatedAt is refreshed`() {
            val t = tool()
            val author = user()
            val note = ToolNote(id = UUID.randomUUID(), tool = t, author = author, text = "old", createdAt = Instant.now().minusSeconds(60), updatedAt = Instant.now().minusSeconds(60))
            whenever(toolNoteRepository.findById(note.id!!)).thenReturn(Optional.of(note))
            whenever(toolNoteRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(toolNoteMapper.toBaseResponse(any())).thenAnswer { baseResponse(it.arguments[0] as ToolNote) }

            service.update(note.id!!, UpdateToolNoteRequest("  Новый текст  "), UserPrincipal(author))

            assertThat(note.text).isEqualTo("Новый текст")
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `note not found throws NotFound`() {
            val id = UUID.randomUUID()
            whenever(toolNoteRepository.findById(id)).thenReturn(Optional.empty())

            assertThatThrownBy { service.delete(id, UserPrincipal(user())) }.isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `wrong author throws Forbidden`() {
            val note = ToolNote(id = UUID.randomUUID(), tool = tool(), author = user(), text = "old")
            whenever(toolNoteRepository.findById(note.id!!)).thenReturn(Optional.of(note))

            assertThatThrownBy { service.delete(note.id!!, UserPrincipal(user())) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        fun `author can delete their own note`() {
            val author = user()
            val note = ToolNote(id = UUID.randomUUID(), tool = tool(), author = author, text = "old")
            whenever(toolNoteRepository.findById(note.id!!)).thenReturn(Optional.of(note))

            service.delete(note.id!!, UserPrincipal(author))

            verify(toolNoteRepository).delete(note)
        }
    }
}
