package ru.heatmap.registry.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import ru.heatmap.registry.domain.ImpactBlock
import ru.heatmap.registry.domain.ImpactRow
import ru.heatmap.registry.repository.ImpactBlockRepository
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImpactApiControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var impactBlockRepository: ImpactBlockRepository

    @Test
    fun `listImpactBlocks is public and returns empty list when nothing seeded`() {
        mockMvc.perform(get("/api/impact"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `listImpactBlocks returns rows sorted by sortOrder`() {
        // code - VARCHAR(16) (см. ImpactBlock.code) - берём короткий суффикс UUID, а не весь UUID целиком.
        // Строки добавляются в rows ДО save() и сохраняются через cascade = ALL на ImpactBlock.rows -
        // если сохранять их отдельно через impactRowRepository, уже загруженная в персистентном
        // контексте коллекция block.rows остаётся пустой (stale), и findAllByOrderBySortOrderAsc()
        // в рамках той же транзакции/сессии не увидит новые строки.
        val block = ImpactBlock(code = "IMP-${UUID.randomUUID().toString().take(8)}", icon = "*", title = "Блок влияния", badgeText = "Достигнуто")
        block.rows.add(ImpactRow(block = block, label = "Вторая", value = "2", sortOrder = 2))
        block.rows.add(ImpactRow(block = block, label = "Первая", value = "1", sortOrder = 1))
        impactBlockRepository.save(block)

        mockMvc.perform(get("/api/impact"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.code=='${block.code}')].rows[0].label").value("Первая"))
            .andExpect(jsonPath("$[?(@.code=='${block.code}')].rows[1].label").value("Вторая"))
    }
}
