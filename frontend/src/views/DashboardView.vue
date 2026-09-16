<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import api, { extractErrorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import AppHeader from '../components/AppHeader.vue'
import StatsGrid from '../components/StatsGrid.vue'
import FilterBar from '../components/FilterBar.vue'
import ToolList from '../components/ToolList.vue'
import ImpactSection from '../components/ImpactSection.vue'
import AddToolModal from '../components/AddToolModal.vue'
import ToolDetailModal from '../components/ToolDetailModal.vue'
import RatingPrompt from '../components/RatingPrompt.vue'
import IconBase from '../components/IconBase.vue'

const auth = useAuthStore()
const router = useRouter()

const stats = ref(null)
const counts = ref({})
const filterOptions = ref({ roles: [], frameworks: [], segments: [] })
const impactBlocks = ref([])
const tools = ref([])
const myTools = ref([])

// Открываем реестр сразу на вкладке "Все инструменты", а не "Топ".
const activeTab = ref('ALL')
const filters = reactive({ role: [], framework: '', segment: [], search: '', sort: 'EFFICIENCY' })

// Пагинация списка инструментов - не больше 5 на странице.
const PAGE_SIZE = 5
const currentPage = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(tools.value.length / PAGE_SIZE)))
const pagedTools = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return tools.value.slice(start, start + PAGE_SIZE)
})

function goToPage(page) {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value)
}

const loadingTools = ref(true)
const loadingTop = ref(true)
const loadingImpact = ref(true)
const errorMessage = ref('')
const modalOpen = ref(false)
const detailOpen = ref(false)
const detailTool = ref(null)
const ratingPromptTool = ref(null)
// Заявка, которую сейчас редактируют в AddToolModal - null означает режим создания новой.
const editingTool = ref(null)

const tabTitles = {
  ALL: '📋 Все инструменты · полный реестр',
  TOP: '🏆 Топ решений банка · проверенные фавориты',
  ACCESS: 'Access · новые инструменты на апробации',
  USAGE: 'Usage · инструменты в активном использовании',
  HABIT: 'Habit · вошли в привычный процесс',
  STANDARD: 'Process Standard · эталонные решения'
}

async function loadTopData() {
  loadingTop.value = true
  try {
    const [statsRes, countsRes, optsRes, impactRes] = await Promise.all([
      api.get('/tools/stats'),
      api.get('/tools/counts'),
      api.get('/tools/filter-options'),
      api.get('/impact')
    ])
    stats.value = statsRes.data
    counts.value = countsRes.data
    filterOptions.value = optsRes.data
    impactBlocks.value = impactRes.data
  } catch (e) {
    errorMessage.value = extractErrorMessage(e, 'Не удалось загрузить сводные данные')
  } finally {
    loadingTop.value = false
    loadingImpact.value = false
  }
}

async function loadTools() {
  loadingTools.value = true
  errorMessage.value = ''
  try {
    const { data } = await api.get('/tools', {
      params: {
        tab: activeTab.value,
        // Бэкенд принимает список ролей/сегментов через запятую в одном параметре
        // (Spring сам разбивает такую строку в List<String>).
        role: filters.role.length ? filters.role.join(',') : undefined,
        framework: filters.framework || undefined,
        segment: filters.segment.length ? filters.segment.join(',') : undefined,
        search: filters.search || undefined,
        sort: filters.sort || undefined
      }
    })
    tools.value = data
    currentPage.value = 1
  } catch (e) {
    errorMessage.value = extractErrorMessage(e, 'Не удалось загрузить реестр инструментов')
  } finally {
    loadingTools.value = false
  }
}

async function loadMine() {
  // Гостю нечего показывать в "моих заявках" - и лишний запрос с гарантированным 401 не нужен.
  if (!auth.isAuthenticated) {
    myTools.value = []
    return
  }
  try {
    const { data } = await api.get('/tools/mine')
    myTools.value = data
  } catch {
    myTools.value = []
  }
}

// Виджет "Мои инструменты на модерации" отслеживает только заявки, требующие внимания
// автора - на модерации или отклонённые (их можно поправить и отправить заново).
// Опубликованный инструмент из виджета убираем - он уже виден в общем реестре, где его
// тоже можно отредактировать (карточка → "Подробнее" → "Редактировать", см. ToolDetailModal);
// такая правка от автора (не администратора) точно так же вернёт инструмент на модерацию.
// Архивированный администратором инструмент сюда тоже не попадает - это не то, что
// требует действий автора прямо сейчас (см. уведомление об архивации).
const myPendingTools = computed(() =>
  myTools.value.filter((t) => t.status === 'PENDING' || t.status === 'REJECTED')
)

function openAddModal() {
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: '/' } })
    return
  }
  editingTool.value = null
  modalOpen.value = true
}

// Редактировать можно свой инструмент в любом статусе - правки опубликованного
// или отклонённого инструмента отправят его на повторную модерацию (см. ToolService.update).
function openEditModal(tool) {
  editingTool.value = tool
  modalOpen.value = true
}

async function onToolUpdated() {
  editingTool.value = null
  await Promise.all([loadMine(), loadTools(), loadTopData()])
}

function resetFilters() {
  filters.role = []
  filters.framework = ''
  filters.segment = []
  filters.search = ''
  filters.sort = 'EFFICIENCY'
}

async function onToolCreated() {
  await Promise.all([loadMine(), loadTopData()])
}

// Общий обработчик удаления: свою заявку отзывает автор, любой инструмент - администратор.
// Возвращает true при успешном удалении, чтобы вызывающий код мог, например, закрыть модалку.
async function onDeleteTool(tool) {
  if (!confirm(`Удалить инструмент «${tool.name}»?`)) return false
  try {
    await api.delete(`/tools/${tool.id}`)
    await Promise.all([loadMine(), loadTools(), loadTopData()])
    return true
  } catch (e) {
    errorMessage.value = extractErrorMessage(e, 'Не удалось удалить инструмент')
    return false
  }
}

// Удаление из модалки "Подробнее": закрываем её только если удаление реально произошло.
async function onDeleteFromDetail(tool) {
  const deleted = await onDeleteTool(tool)
  if (deleted) detailOpen.value = false
}

// Правка из модалки "Подробнее" (см. ToolDetailModal) - закрываем её и открываем ту же
// форму редактирования, что и из виджета "Мои инструменты на модерации".
function onEditFromDetail(tool) {
  detailOpen.value = false
  openEditModal(tool)
}

// Просмотр засчитывается только по клику "Подробнее", не за сам факт показа карточки.
async function onViewTool(tool) {
  try {
    const { data } = await api.post(`/tools/${tool.id}/view`)
    Object.assign(tool, data)
  } catch {
    // счётчик просмотров не критичен - молча открываем карточку даже если запрос не прошёл
  } finally {
    detailTool.value = tool
    detailOpen.value = true
  }
}

// После скачивания предлагаем пользователю оценить инструмент по 5-балльной шкале.
// Список "Мои инструменты" (в меню профиля) сам подгружает данные при открытии.
function onToolDownloaded(tool) {
  ratingPromptTool.value = tool
}

async function onRated() {
  await Promise.all([loadTools(), loadTopData()])
}

let debounceHandle = null
watch(
  () => [activeTab.value, filters.role, filters.framework, filters.segment, filters.search, filters.sort],
  () => {
    clearTimeout(debounceHandle)
    debounceHandle = setTimeout(loadTools, 200)
  }
)

onMounted(() => {
  loadTopData()
  loadTools()
  loadMine()
})
</script>

<template>
  <div>
    <AppHeader />

    <main class="container">
      <div class="page-heading">
        <div>
          <h1>Реестр AI-инструментов</h1>
          <p>CLI-скиллы, агенты и фреймворки, применяемые в цикле разработки</p>
        </div>
        <button class="btn btn-primary" type="button" @click="openAddModal">
          <IconBase name="plus" :size="15" /> Добавить инструмент
        </button>
      </div>

      <p v-if="errorMessage" class="error-text" style="margin-bottom: 16px;">{{ errorMessage }}</p>

      <StatsGrid v-model="activeTab" :stats="stats" :counts="counts" :loading="loadingTop" />

      <div v-if="myPendingTools.length" class="my-requests panel">
        <div class="my-requests-title"><IconBase name="clock" :size="15" /> Мои инструменты на модерации</div>
        <div class="my-requests-list">
          <div v-for="t in myPendingTools" :key="t.id" class="my-request-row">
            <div class="my-request-main">
              <span class="my-request-name">{{ t.name }}</span>
              <span
                class="badge"
                :class="{ PENDING: 'badge-warn', PUBLISHED: 'badge-accent', REJECTED: 'badge-danger' }[t.status]"
              >
                {{ { PENDING: 'На модерации', PUBLISHED: 'Опубликован', REJECTED: 'Отклонено' }[t.status] }}
              </span>
            </div>
            <p v-if="t.status === 'REJECTED' && t.rejectionReason" class="my-request-reason">
              Причина отклонения: {{ t.rejectionReason }}
            </p>
            <div class="my-request-actions">
              <button class="btn btn-ghost btn-sm" type="button" @click="openEditModal(t)">Редактировать</button>
              <button class="btn btn-ghost btn-sm" type="button" @click="onDeleteTool(t)">Отозвать</button>
            </div>
          </div>
        </div>
      </div>

      <FilterBar
        v-model:role="filters.role"
        v-model:framework="filters.framework"
        v-model:segment="filters.segment"
        v-model:search="filters.search"
        :filter-options="filterOptions"
        :result-count="tools.length"
        @reset="resetFilters"
      />

      <ToolList
        :title="tabTitles[activeTab]"
        :tools="pagedTools"
        :loading="loadingTools"
        v-model:sort="filters.sort"
        @view="onViewTool"
      />

      <div v-if="!loadingTools && totalPages > 1" class="pagination">
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          :disabled="currentPage === 1"
          @click="goToPage(currentPage - 1)"
        >
          ← Назад
        </button>
        <span class="pagination-pages">
          <button
            v-for="page in totalPages"
            :key="page"
            type="button"
            class="pagination-page"
            :class="{ active: page === currentPage }"
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
        </span>
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          :disabled="currentPage === totalPages"
          @click="goToPage(currentPage + 1)"
        >
          Вперёд →
        </button>
      </div>

      <ImpactSection :blocks="impactBlocks" :loading="loadingImpact" />

      <footer class="page-footer">
        <span>HeatMap · MVP · 4 этапа зрелости: Access → Usage → Habit → Process Standard</span>
      </footer>
    </main>

    <AddToolModal
      v-model="modalOpen"
      :filter-options="filterOptions"
      :edit-tool="editingTool"
      @created="onToolCreated"
      @updated="onToolUpdated"
    />
    <ToolDetailModal
      v-model="detailOpen"
      :tool="detailTool"
      @delete="onDeleteFromDetail"
      @downloaded="onToolDownloaded"
      @edit="onEditFromDetail"
    />
    <RatingPrompt :tool="ratingPromptTool" @close="ratingPromptTool = null" @rated="onRated" />
  </div>
</template>

<style scoped>
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.page-heading h1 {
  font-size: 24px;
  margin: 0 0 4px;
}

.page-heading p {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.my-requests {
  padding: 14px 18px;
  margin-bottom: 20px;
}

.my-requests-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 10px;
}

.my-requests-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.my-request-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13.5px;
  padding: 8px 0;
  border-bottom: 1px solid var(--surface-muted);
}
.my-request-row:last-child {
  border-bottom: none;
}

.my-request-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.my-request-name {
  font-weight: 600;
  flex: 1;
}

.my-request-reason {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.my-request-actions {
  display: flex;
  gap: 8px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin: 18px 0 4px;
}

.pagination-pages {
  display: flex;
  gap: 4px;
}

.pagination-page {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}
.pagination-page:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}
.pagination-page.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #ffffff;
}

.page-footer {
  margin-top: 32px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
  font-size: 12.5px;
  color: var(--text-muted);
}
</style>
