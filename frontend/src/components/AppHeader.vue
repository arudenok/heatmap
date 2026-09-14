<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'
import EditProfileModal from './EditProfileModal.vue'
import MyDownloadsModal from './MyDownloadsModal.vue'
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

      <nav class="header-nav">
        <RouterLink to="/" class="nav-link" active-class="nav-link-active" title="Реестр">
          <IconBase name="layers" :size="15" /> <span class="nav-link-label">Реестр</span>
        </RouterLink>
        <RouterLink
          v-if="auth.isAdmin"
          to="/admin"
          class="nav-link"
          active-class="nav-link-active"
          title="Администрирование"
        >
          <IconBase name="shield" :size="15" /> <span class="nav-link-label">Администрирование</span>
        </RouterLink>
      </nav>

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

    <EditProfileModal v-model="profileOpen" />
    <MyDownloadsModal v-model="myDownloadsOpen" />
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

.header-nav {
  display: flex;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
  flex-shrink: 0;
}
.nav-link:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}
.nav-link-active {
  background: var(--accent-soft);
  color: var(--accent-dark);
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
  cursor: pointer;
  flex-shrink: 0;
  transition: border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}
.theme-toggle:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
  background: var(--surface-muted);
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
   на средних ширинах (~720-900px) полная подпись бренда + оба пункта меню
   с текстом + полное имя пользователя одновременно не помещаются и текст
   начинает наезжать друг на друга. Поэтому самое "тяжёлое" (имя пользователя
   и подписи пунктов меню) прячем одним и тем же брейкпоинтом. */
@media (max-width: 960px) {
  .app-header-inner { gap: 14px; }
  .brand-sub { display: none; }
  .header-nav { gap: 2px; }
  .nav-link-label { display: none; }
  .nav-link { padding: 8px; }
  .user-name { display: none; }
}

@media (max-width: 480px) {
  .app-header-inner { gap: 8px; }
  .brand-title { font-size: 14px; }
  .theme-toggle { width: 30px; height: 30px; }
  .header-user { padding: 4px; gap: 4px; }
}
</style>
