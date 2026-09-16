<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../services/api'
import IconBase from './IconBase.vue'
import EditProfileModal from './EditProfileModal.vue'
import MyDownloadsModal from './MyDownloadsModal.vue'
import ToolNotesModal from './ToolNotesModal.vue'
import { getEffectiveTheme, toggleTheme } from '../utils/theme'

const auth = useAuthStore()
const router = useRouter()
const menuOpen = ref(false)
const profileOpen = ref(false)
const myDownloadsOpen = ref(false)
const currentTheme = ref(getEffectiveTheme())

function onToggleTheme() {
  currentTheme.value = toggleTheme()
}

// ===== Уведомления: админу - о новых заявках, автору - о результате модерации =====
const notifOpen = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const notifLoading = ref(false)
// Непрочитанные и прочитанные - на разных вкладках, чтобы старые не мешали видеть новые.
const notifTab = ref('unread')
const unreadNotifs = computed(() => notifications.value.filter((n) => !n.read))
const readNotifs = computed(() => notifications.value.filter((n) => n.read))
const displayedNotifs = computed(() => (notifTab.value === 'unread' ? unreadNotifs.value : readNotifs.value))
let pollHandle = null

const notifMeta = {
  NEW_SUBMISSION: { icon: 'inbox' },
  SUBMISSION_APPROVED: { icon: 'check' },
  SUBMISSION_REJECTED: { icon: 'x' },
  NEW_TOOL_NOTE: { icon: 'note' },
  TOOL_ARCHIVED: { icon: 'archive' }
}

// Счётчик заявок, ждущих модерации - виден админу рядом со ссылкой "Администрирование"
// в шапке (а не только внутри самой страницы администрирования), обновляется тем же опросом.
const pendingModerationCount = ref(0)

async function loadPendingModerationCount() {
  if (!auth.isAdmin) return
  try {
    const { data } = await api.get('/admin/tools/pending')
    pendingModerationCount.value = data.length
  } catch {
    // не критично - молча оставляем прежнее значение
  }
}

async function loadUnreadCount() {
  if (!auth.isAuthenticated) return
  try {
    const { data } = await api.get('/notifications/unread-count')
    unreadCount.value = data.count
  } catch {
    // молча - иконка уведомлений не критична для остального функционала
  }
}

// Роль (и остальные данные профиля) другого пользователя администратор может изменить в
// любой момент, пока тот уже находится в открытой сессии (см. AdminUsersPanel.toggleRole) -
// без периодического обновления собственных данных пользователя изменение роли применится
// только на бэкенде (JwtAuthenticationFilter каждый раз читает роль из БД заново), а кнопки
// администратора в интерфейсе останутся видны/скрыты по старой роли до перезахода. Опрос по
// тому же интервалу, что и уведомления/счётчик модерации, устраняет эту рассинхронизацию
// в обе стороны без необходимости перелогиниваться. 401 (например, аккаунт заблокировали)
// обработает общий интерцептор api.js - он и так разлогинивает при истёкшем/невалидном токене.
async function refreshOwnRole() {
  if (!auth.isAuthenticated) return
  try {
    await auth.refreshMe()
  } catch {
    // молча - не критично для остального функционала, следующий опрос повторит попытку
  }
}

async function loadNotifications() {
  notifLoading.value = true
  try {
    const { data } = await api.get('/notifications')
    notifications.value = data
  } catch {
    notifications.value = []
  } finally {
    notifLoading.value = false
  }
}

function toggleNotifications() {
  notifOpen.value = !notifOpen.value
  if (notifOpen.value) {
    notifTab.value = 'unread'
    loadNotifications()
  }
}

// Заметки, открытые из уведомления - отдельная модалка поверх текущей страницы (см. ниже),
// а не переход в раздел администрирования: там пришлось бы ещё раз искать нужный инструмент
// и нажимать "Заметки" вручную. Работает одинаково для непрочитанных и уже прочитанных
// уведомлений - переход не зависит от того, отмечали мы уведомление прочитанным выше или нет.
const notesModalOpen = ref(false)
const notesModalTool = ref(null)

async function onNotificationClick(notification) {
  if (!notification.read) {
    try {
      await api.post(`/notifications/${notification.id}/read`)
      notification.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // не критично - просто оставляем непрочитанным
    }
  }
  notifOpen.value = false

  if (notification.type === 'NEW_TOOL_NOTE' && auth.isAdmin) {
    notesModalTool.value = { id: notification.toolId, name: notification.toolName }
    notesModalOpen.value = true
    return
  }

  // Новую заявку удобно сразу открыть на странице администрирования (вкладка "Модерация" по умолчанию).
  if (notification.type === 'NEW_SUBMISSION' && auth.isAdmin) {
    router.push('/admin')
  }
}

async function markAllRead() {
  try {
    await api.post('/notifications/read-all')
    notifications.value.forEach((n) => (n.read = true))
    unreadCount.value = 0
  } catch {
    // не критично
  }
}

function formatNotifDate(value) {
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

watch(
  () => auth.isAuthenticated,
  (isAuth) => {
    if (isAuth) {
      loadUnreadCount()
      loadPendingModerationCount()
      refreshOwnRole()
    } else {
      notifications.value = []
      unreadCount.value = 0
      notifOpen.value = false
      pendingModerationCount.value = 0
    }
  }
)

onMounted(() => {
  loadUnreadCount()
  loadPendingModerationCount()
  refreshOwnRole()
  // Периодически обновляем счётчики и собственную роль - без полноценных веб-сокетов этого
  // достаточно, чтобы бейджи не "залипали" надолго и админ-элементы не путались со старой
  // ролью после того, как её изменили в другой сессии (см. refreshOwnRole выше). Раньше был
  // опрос раз в 20с - ощущалось как слишком частое "мигание" уведомлений, поэтому интервал увеличен.
  pollHandle = setInterval(() => {
    loadUnreadCount()
    loadPendingModerationCount()
    refreshOwnRole()
  }, 60000)
})

onUnmounted(() => {
  clearInterval(pollHandle)
})

function logout() {
  auth.logout()
  // После выхода пользователь должен оказаться на главной, а не на форме входа.
  router.replace({ name: 'dashboard' })
}

function openProfile() {
  menuOpen.value = false
  profileOpen.value = true
}

function openMyDownloads() {
  menuOpen.value = false
  myDownloadsOpen.value = true
}

// Простая локальная директива для закрытия выпадающего меню по клику снаружи
const vClickOutside = {
  mounted(el, binding) {
    el.__clickOutsideHandler = (event) => {
      if (!el.contains(event.target)) binding.value(event)
    }
    document.addEventListener('click', el.__clickOutsideHandler)
  },
  unmounted(el) {
    document.removeEventListener('click', el.__clickOutsideHandler)
  }
}
</script>

<template>
  <header class="app-header">
    <div class="container app-header-inner">
      <RouterLink to="/" class="brand">
        <span class="brand-icon"><IconBase name="layers" :size="20" /></span>
        <div>
          <div class="brand-title">HeatMap</div>
          <div class="brand-sub">Реестр AI-инструментов PDLC</div>
        </div>
      </RouterLink>

      <!-- Все действия справа сгруппированы в один кластер (margin-left: auto),
           чтобы админ-кнопка не "плавала" отдельно от остальных иконок шапки. -->
      <div class="header-actions">
        <RouterLink
          v-if="auth.isAdmin"
          to="/admin"
          class="theme-toggle admin-toggle"
          active-class="admin-toggle-active"
          title="Администрирование"
        >
          <IconBase name="shield" :size="17" />
          <span v-if="pendingModerationCount" class="icon-badge">{{ pendingModerationCount > 9 ? '9+' : pendingModerationCount }}</span>
        </RouterLink>

        <div
          v-if="auth.isAuthenticated"
          class="notif-wrap"
          v-click-outside="() => (notifOpen = false)"
        >
          <button
            type="button"
            class="theme-toggle notif-toggle"
            title="Уведомления"
            @click="toggleNotifications"
          >
            <IconBase name="bell" :size="17" />
            <span v-if="unreadCount" class="icon-badge">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
          </button>

          <div v-if="notifOpen" class="notif-dropdown">
            <div class="notif-dropdown-header">
              <span>Уведомления</span>
              <button
                v-if="unreadNotifs.length"
                type="button"
                class="notif-mark-all"
                @click="markAllRead"
              >
                Прочитать все
              </button>
            </div>

            <div class="notif-tab-bar">
              <button
                type="button"
                class="notif-tab-btn"
                :class="{ active: notifTab === 'unread' }"
                @click="notifTab = 'unread'"
              >
                Новые
                <span v-if="unreadNotifs.length" class="notif-tab-count">{{ unreadNotifs.length }}</span>
              </button>
              <button
                type="button"
                class="notif-tab-btn"
                :class="{ active: notifTab === 'read' }"
                @click="notifTab = 'read'"
              >
                Прочитанные
              </button>
            </div>

            <div v-if="notifLoading" class="skeleton" style="height: 40px; margin: 8px;"></div>
            <p v-else-if="!displayedNotifs.length" class="notif-empty">
              {{ notifTab === 'unread' ? 'Нет новых уведомлений' : 'Прочитанных уведомлений нет' }}
            </p>
            <div v-else class="notif-list">
              <button
                v-for="n in displayedNotifs"
                :key="n.id"
                type="button"
                class="notif-item"
                :class="{ unread: !n.read }"
                @click="onNotificationClick(n)"
              >
                <IconBase :name="notifMeta[n.type]?.icon || 'inbox'" :size="15" />
                <span class="notif-item-text">
                  <span class="notif-item-message">{{ n.message }}</span>
                  <span class="notif-item-date">{{ formatNotifDate(n.createdAt) }}</span>
                </span>
                <span v-if="!n.read" class="notif-dot"></span>
              </button>
            </div>
          </div>
        </div>

        <button
          type="button"
          class="theme-toggle"
          :title="currentTheme === 'dark' ? 'Светлая тема' : 'Тёмная тема'"
          @click="onToggleTheme"
        >
          <IconBase :name="currentTheme === 'dark' ? 'sun' : 'moon'" :size="17" />
        </button>

        <div v-if="auth.isAuthenticated" class="header-user" @click="menuOpen = !menuOpen" v-click-outside="() => (menuOpen = false)">
          <span class="user-avatar"><IconBase name="user" :size="16" /></span>
          <span class="user-name">{{ auth.displayName }}</span>
          <IconBase name="chevronDown" :size="14" />

          <div v-if="menuOpen" class="user-menu">
            <div class="user-menu-role">
              <IconBase name="shield" :size="13" />
              {{ auth.isAdmin ? 'Администратор' : 'Пользователь' }}
            </div>
            <button class="user-menu-item" @click="openProfile">
              <IconBase name="edit" :size="15" /> Профиль
            </button>
            <button class="user-menu-item" @click="openMyDownloads">
              <IconBase name="download" :size="15" /> Мои инструменты
            </button>
            <button class="user-menu-item" @click="logout">
              <IconBase name="logout" :size="15" /> Выйти
            </button>
          </div>
        </div>

        <div v-else class="header-guest">
          <!-- Регистрация не отдельной кнопкой в шапке - ссылка на неё уже есть внутри формы входа. -->
          <RouterLink to="/login" class="btn btn-primary btn-sm">Войти</RouterLink>
        </div>
      </div>
    </div>

    <EditProfileModal v-model="profileOpen" />
    <MyDownloadsModal v-model="myDownloadsOpen" />
    <ToolNotesModal v-model="notesModalOpen" :tool="notesModalTool" />
  </header>
</template>

<style scoped>
.app-header {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 20;
}

.app-header-inner {
  padding-top: 14px;
  padding-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
  min-width: 0;
}

.brand-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  background: var(--accent-soft);
  color: var(--accent-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-title {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.2px;
  line-height: 1.2;
}

.brand-sub {
  font-size: 11px;
  color: var(--text-muted);
}

/* Все действия справа (админка, уведомления, тема, профиль) - один кластер,
   прижатый к правому краю, чтобы не оставалось "плавающих" элементов у логотипа. */
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-shrink: 0;
}

.header-guest {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-secondary);
  text-decoration: none;
  cursor: pointer;
  flex-shrink: 0;
  transition: border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}
.theme-toggle:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
  background: var(--surface-muted);
}

.admin-toggle {
  position: relative;
}
.admin-toggle-active {
  border-color: var(--accent);
  color: var(--accent-dark);
  background: var(--accent-soft);
}

.notif-wrap {
  position: relative;
  flex-shrink: 0;
}

.notif-toggle {
  position: relative;
}

.icon-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 999px;
  background: var(--danger, #d64545);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.notif-dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 320px;
  max-height: 380px;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  z-index: 30;
}

.notif-dropdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  position: sticky;
  top: 0;
  background: var(--surface);
}

.notif-mark-all {
  background: none;
  border: none;
  color: var(--accent-dark);
  font-size: 11.5px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: normal;
  cursor: pointer;
  padding: 0;
}
.notif-mark-all:hover {
  text-decoration: underline;
}

.notif-tab-bar {
  display: flex;
  gap: 4px;
  margin: 8px;
  padding: 3px;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
}

.notif-tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  background: none;
  border: none;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
}
.notif-tab-btn:hover {
  color: var(--text-primary);
}
.notif-tab-btn.active {
  background: var(--surface);
  color: var(--accent-dark);
}

.notif-tab-count {
  font-size: 10px;
  font-weight: 700;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-dark);
}
.notif-tab-btn.active .notif-tab-count {
  background: var(--accent);
  color: #fff;
}

.notif-empty {
  padding: 18px 14px;
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  text-align: center;
}

.notif-list {
  display: flex;
  flex-direction: column;
  padding: 4px;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  background: none;
  border: none;
  text-align: left;
  padding: 9px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-primary);
}
.notif-item:hover {
  background: var(--surface-muted);
}
.notif-item.unread {
  background: var(--accent-soft);
}
.notif-item.unread:hover {
  background: var(--accent-soft);
  filter: brightness(0.97);
}

.notif-item-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.notif-item-message {
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.35;
  white-space: normal;
}

.notif-item-date {
  font-size: 11px;
  color: var(--text-muted);
}

.notif-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent-dark);
  flex-shrink: 0;
  margin-top: 4px;
}

@media (max-width: 480px) {
  .notif-dropdown { width: 280px; }
}

.header-user {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 10px 6px 6px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  flex-shrink: 0;
}
.header-user:hover {
  background: var(--surface-muted);
  border-color: var(--border);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface-muted);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  min-width: 180px;
  padding: 6px;
}

.user-menu-role {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 4px;
}

.user-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  text-align: left;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
}
.user-menu-item:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

/* Порог подобран так, чтобы не оставалось "мёртвой зоны" между брейкпоинтами -
   на средних ширинах (~720-900px) полная подпись бренда + полное имя пользователя
   одновременно не помещаются и текст начинает наезжать друг на друга. Поэтому
   самое "тяжёлое" (подпись бренда и имя пользователя) прячем одним брейкпоинтом. */
@media (max-width: 960px) {
  .app-header-inner { gap: 14px; }
  .brand-sub { display: none; }
  .header-actions { gap: 6px; }
  .user-name { display: none; }
}

@media (max-width: 480px) {
  .app-header-inner { gap: 8px; }
  .brand-title { font-size: 14px; }
  .theme-toggle { width: 30px; height: 30px; }
  .header-user { padding: 4px; gap: 4px; }
}
</style>
