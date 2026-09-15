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
        placeholder="Поиск по имени или логину Сигма..."
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
          <th>Роль</th>
          <th>Статус</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>
            <div class="user-cell-name">{{ user.fullName }}</div>
            <div class="user-cell-username">Логин Сигма: {{ user.username }}</div>
          </td>
          <td>
            <span class="badge" :class="user.role === 'ADMIN' ? 'badge-accent' : 'badge-info'">{{ user.role }}</span>
          </td>
          <td>
            <span class="badge" :class="user.enabled ? 'badge-accent' : 'badge-danger'">
              {{ user.enabled ? 'Активен' : 'Заблокирован' }}
            </span>
          </td>
          <td class="user-actions-cell">
            <div class="user-actions">
              <button
                class="btn btn-ghost btn-sm role-toggle-btn"
                type="button"
                :disabled="busyId === user.id || user.id === auth.user?.id"
                @click="toggleRole(user)"
              >
                {{ user.role === 'ADMIN' ? 'Сделать пользователем' : 'Сделать админом' }}
              </button>
              <button
                class="btn btn-danger-ghost btn-sm"
                type="button"
                :disabled="busyId === user.id || user.id === auth.user?.id"
                @click="deleteUser(user)"
              >
                <IconBase name="trash" :size="13" /> Удалить
              </button>
            </div>
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

/* Последняя колонка таблицы обычно шире своего содержимого (остальные три колонки
   не занимают всю ширину панели) - без этого кнопки повисали слева, а справа
   оставалась пустая полоса. */
.user-actions-cell {
  text-align: right;
}

.user-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

/* Текст на кнопке смены роли разной длины ("Сделать пользователем" длиннее, чем
   "Сделать админом") - фиксируем ширину, чтобы кнопка выглядела одинаково и стояла
   на одном месте в каждой строке, а не "прыгала" из-за разной ширины текста. */
.role-toggle-btn {
  width: 190px;
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
