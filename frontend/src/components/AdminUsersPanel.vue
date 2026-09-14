<script setup>
import { onMounted, ref, watch } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'

const auth = useAuthStore()
const users = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const search = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/admin/users', {
      params: { search: search.value.trim() || undefined }
    })
    users.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить список пользователей')
  } finally {
    loading.value = false
  }
}

let debounceHandle = null
watch(search, () => {
  clearTimeout(debounceHandle)
  debounceHandle = setTimeout(load, 250)
})

async function toggleRole(user) {
  const nextRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  busyId.value = user.id
  try {
    const { data } = await api.patch(`/admin/users/${user.id}/role`, { role: nextRole })
    Object.assign(user, data)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось изменить роль')
  } finally {
    busyId.value = null
  }
}

async function toggleEnabled(user) {
  busyId.value = user.id
  try {
    const { data } = await api.patch(`/admin/users/${user.id}/enabled`, { enabled: !user.enabled })
    Object.assign(user, data)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось изменить статус пользователя')
  } finally {
    busyId.value = null
  }
}

async function deleteUser(user) {
  if (!confirm(`Удалить пользователя «${user.fullName}» (@${user.username})? Это действие необратимо.`)) return
  busyId.value = user.id
  try {
    await api.delete(`/admin/users/${user.id}`)
    users.value = users.value.filter((u) => u.id !== user.id)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось удалить пользователя')
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="users" :size="16" /> Пользователи <span class="badge badge-info">{{ users.length }}</span></h3>
      <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
    </div>

    <div class="users-search">
      <IconBase name="search" :size="14" />
      <input
        v-model="search"
        type="text"
        placeholder="Поиск по имени, логину или логину Сигма..."
        class="users-search-input"
      />
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <p v-if="!loading && !users.length" class="empty-text">Ничего не найдено</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <table v-else-if="users.length" class="users-table">
      <thead>
        <tr>
          <th>Пользователь</th>
          <th>Логин Сигма</th>
          <th>Роль</th>
          <th>Статус</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>
            <div class="user-cell-name">{{ user.fullName }}</div>
            <div class="user-cell-username">@{{ user.username }}</div>
          </td>
          <td>{{ user.email }}</td>
          <td>
            <span class="badge" :class="user.role === 'ADMIN' ? 'badge-accent' : 'badge-info'">{{ user.role }}</span>
          </td>
          <td>
            <span class="badge" :class="user.enabled ? 'badge-accent' : 'badge-danger'">
              {{ user.enabled ? 'Активен' : 'Заблокирован' }}
            </span>
          </td>
          <td class="user-actions">
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="busyId === user.id || user.id === auth.user?.id"
              @click="toggleRole(user)"
            >
              {{ user.role === 'ADMIN' ? 'Сделать пользователем' : 'Сделать админом' }}
            </button>
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="busyId === user.id || user.id === auth.user?.id"
              @click="toggleEnabled(user)"
            >
              {{ user.enabled ? 'Заблокировать' : 'Разблокировать' }}
            </button>
            <button
              class="btn btn-danger-ghost btn-sm"
              type="button"
              :disabled="busyId === user.id || user.id === auth.user?.id"
              @click="deleteUser(user)"
            >
              <IconBase name="trash" :size="13" /> Удалить
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.admin-panel {
  padding: 20px 22px;
}

.admin-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.users-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  margin-bottom: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  color: var(--text-muted);
  max-width: 360px;
}

.users-search-input {
  border: none;
  background: none;
  outline: none;
  font-size: 13.5px;
  color: var(--text-primary);
  flex: 1;
}

.empty-text {
  font-size: 13.5px;
  color: var(--text-muted);
  padding: 10px 0;
}

.users-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}

.users-table th {
  text-align: left;
  font-size: 11.5px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--text-muted);
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
}

.users-table td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--surface-muted);
  vertical-align: middle;
}

.user-cell-name {
  font-weight: 600;
}
.user-cell-username {
  font-size: 12px;
  color: var(--text-muted);
}

.user-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 800px) {
  .users-table thead { display: none; }
  .users-table, .users-table tbody, .users-table tr, .users-table td {
    display: block;
    width: 100%;
  }
  .users-table tr {
    border-bottom: 1px solid var(--border);
    padding: 10px 0;
  }
}
</style>
