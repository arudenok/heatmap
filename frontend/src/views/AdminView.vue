<script setup>
import { ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import AdminModerationPanel from '../components/AdminModerationPanel.vue'
import AdminToolsPanel from '../components/AdminToolsPanel.vue'
import AdminUsersPanel from '../components/AdminUsersPanel.vue'
import AdminImpactPanel from '../components/AdminImpactPanel.vue'
import IconBase from '../components/IconBase.vue'

const activeSection = ref('moderation')

const sections = [
  { key: 'moderation', label: 'Модерация', icon: 'inbox' },
  { key: 'tools', label: 'Инструменты', icon: 'layers' },
  { key: 'users', label: 'Пользователи', icon: 'users' },
  { key: 'impact', label: 'Метрики влияния', icon: 'gauge' }
]
</script>

<template>
  <div>
    <AppHeader />

    <main class="container">
      <div class="page-heading">
        <div>
          <h1><IconBase name="shield" :size="20" /> Администрирование</h1>
          <p>Модерация заявок, управление пользователями и редактирование ключевых метрик</p>
        </div>
      </div>

      <div class="admin-tabs">
        <button
          v-for="section in sections"
          :key="section.key"
          type="button"
          class="admin-tab"
          :class="{ active: activeSection === section.key }"
          @click="activeSection = section.key"
        >
          <IconBase :name="section.icon" :size="14" /> {{ section.label }}
        </button>
      </div>

      <AdminModerationPanel v-if="activeSection === 'moderation'" />
      <AdminToolsPanel v-else-if="activeSection === 'tools'" />
      <AdminUsersPanel v-else-if="activeSection === 'users'" />
      <AdminImpactPanel v-else-if="activeSection === 'impact'" />
    </main>
  </div>
</template>

<style scoped>
.page-heading {
  margin-bottom: 22px;
}

.page-heading h1 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 24px;
  margin: 0 0 4px;
}

.page-heading p {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.admin-tabs {
  display: flex;
  gap: 4px;
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 4px;
  border-radius: var(--radius-md);
  margin-bottom: 20px;
  width: fit-content;
}

.admin-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-weight: 600;
  font-size: 13.5px;
  cursor: pointer;
  background: transparent;
  color: var(--text-secondary);
  border: none;
}
.admin-tab:hover:not(.active) {
  background: var(--surface-muted);
  color: var(--text-primary);
}
.admin-tab.active {
  background: var(--accent);
  color: #ffffff;
}
</style>
