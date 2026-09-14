import { defineStore } from 'pinia'
import api from '../services/api'

function loadStoredUser() {
  try {
    const raw = localStorage.getItem('heatmap_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('heatmap_token') || null,
    user: loadStoredUser()
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'ADMIN',
    displayName: (state) => state.user?.fullName || state.user?.username || ''
  },

  actions: {
    persist(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('heatmap_token', token)
      localStorage.setItem('heatmap_user', JSON.stringify(user))
    },

    async login(usernameOrEmail, password) {
      const { data } = await api.post('/auth/login', { usernameOrEmail, password })
      this.persist(data.token, data.user)
      return data
    },

    async register(payload) {
      const { data } = await api.post('/auth/register', payload)
      this.persist(data.token, data.user)
      return data
    },

    async refreshMe() {
      const { data } = await api.get('/auth/me')
      this.user = data
      localStorage.setItem('heatmap_user', JSON.stringify(data))
      return data
    },

    async updateProfile(payload) {
      const { data } = await api.patch('/auth/me', payload)
      this.user = data
      localStorage.setItem('heatmap_user', JSON.stringify(data))
      return data
    },

    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('heatmap_token')
      localStorage.removeItem('heatmap_user')
    }
  }
})
