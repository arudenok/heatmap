package ru.heatmap.registry.mapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.heatmap.registry.domain.AiTool
import ru.heatmap.registry.domain.AppUser
import ru.heatmap.registry.domain.Role
import ru.heatmap.registry.domain.ToolNote
import java.util.UUID

class ToolNoteMapperTest {

    private val mapper: ToolNoteMapper = ToolNoteMapperImpl()

    @Test
    fun `maps nested tool and author fields and forces canManage to false`() {
        val tool = AiTool(
            id = UUID.randomUUID(),
            name = "Tool",
            description = "Desc",
            ownerName = "Owner"
        )
        val author = AppUser(
            id = UUID.randomUUID(),
            username = "u",
            passwordHash = "h",
            fullName = "Author Name",
            role = Role.ADMIN
        )
        val note = ToolNote(id = UUID.randomUUID(), tool = tool, author = author, text = "Note text")

        val response = mapper.toBaseResponse(note)

        assertThat(response.toolId).isEqualTo(tool.id)
        assertThat(response.authorId).isEqualTo(author.id)
        assertThat(response.authorName).isEqualTo("Author Name")
        assertThat(response.text).isEqualTo("Note text")
        assertThat(response.canManage).isFalse()
    }
}
