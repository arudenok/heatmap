import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn()
  }
}))

import api from '../../services/api'
import { useAuthStore } from '../auth'

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('starts unauthenticated when nothing is stored', () => {
      const auth = useAuthStore()
      expect(auth.token).toBeNull()
      expect(auth.user).toBeNull()
      expect(auth.isAuthenticated).toBe(false)
    })

    it('hydrates from localStorage on creation', () => {
      localStorage.setItem('heatmap_token', 'tok-1')
      localStorage.setItem('heatmap_user', JSON.stringify({ username: '1001', role: 'USER' }))
      const auth = useAuthStore()
      expect(auth.token).toBe('tok-1')
      expect(auth.user).toEqual({ username: '1001', role: 'USER' })
    })

    it('tolerates corrupted user JSON in localStorage', () => {
      localStorage.setItem('heatmap_user', '{not-json')
      const auth = useAuthStore()
      expect(auth.user).toBeNull()
    })
  })

  describe('getters', () => {
    it('isAuthenticated reflects presence of a token', () => {
      const auth = useAuthStore()
      expect(auth.isAuthenticated).toBe(false)
      auth.persist('tok', { username: '1001', role: 'USER' })
      expect(auth.isAuthenticated).toBe(true)
    })

    it('isAdmin is true only for ADMIN role', () => {
      const auth = useAuthStore()
      auth.persist('tok', { username: '1000', role: 'ADMIN' })
      expect(auth.isAdmin).toBe(true)
      auth.persist('tok', { username: '1001', role: 'USER' })
      expect(auth.isAdmin).toBe(false)
    })

    it('isAdmin is false with no user', () => {
      const auth = useAuthStore()
      expect(auth.isAdmin).toBe(false)
    })

    it('displayName prefers fullName, falls back to username, then empty string', () => {
      const auth = useAuthStore()
      expect(auth.displayName).toBe('')
      auth.persist('tok', { username: '1001' })
      expect(auth.displayName).toBe('1001')
      auth.persist('tok', { username: '1001', fullName: 'Иванов И.И.' })
      expect(auth.displayName).toBe('Иванов И.И.')
    })
  })

  describe('persist', () => {
    it('stores token and user both in state and localStorage', () => {
      const auth = useAuthStore()
      auth.persist('tok-2', { username: '1001' })
      expect(auth.token).toBe('tok-2')
      expect(auth.user).toEqual({ username: '1001' })
      expect(localStorage.getItem('heatmap_token')).toBe('tok-2')
      expect(localStorage.getItem('heatmap_user')).toBe(JSON.stringify({ username: '1001' }))
    })
  })

  describe('login', () => {
    it('posts credentials and persists the response', async () => {
      api.post.mockResolvedValue({ data: { token: 'tok-3', user: { username: '1001' } } })
      const auth = useAuthStore()
      const result = await auth.login('1001', 'user123')
      expect(api.post).toHaveBeenCalledWith('/auth/login', { username: '1001', password: 'user123' })
      expect(auth.token).toBe('tok-3')
      expect(auth.user).toEqual({ username: '1001' })
      expect(result).toEqual({ token: 'tok-3', user: { username: '1001' } })
    })

    it('propagates rejection without persisting anything', async () => {
      api.post.mockRejectedValue(new Error('bad credentials'))
      const auth = useAuthStore()
      await expect(auth.login('1001', 'wrong')).rejects.toThrow('bad credentials')
      expect(auth.token).toBeNull()
    })
  })

  describe('register', () => {
    it('posts payload and persists the response', async () => {
      api.post.mockResolvedValue({ data: { token: 'tok-4', user: { username: '9999' } } })
      const auth = useAuthStore()
      const payload = { username: '9999', password: 'secret1', fullName: 'Новый Пользователь' }
      await auth.register(payload)
      expect(api.post).toHaveBeenCalledWith('/auth/register', payload)
      expect(auth.token).toBe('tok-4')
    })
  })

  describe('refreshMe', () => {
    it('fetches and stores the current user without touching the token', async () => {
      const auth = useAuthStore()
      auth.persist('tok-5', { username: '1001' })
      api.get.mockResolvedValue({ data: { username: '1001', fullName: 'Иванов И.И.' } })
      const result = await auth.refreshMe()
      expect(api.get).toHaveBeenCalledWith('/auth/me')
      expect(auth.user).toEqual({ username: '1001', fullName: 'Иванов И.И.' })
      expect(auth.token).toBe('tok-5')
      expect(result).toEqual({ username: '1001', fullName: 'Иванов И.И.' })
    })
  })

  describe('updateProfile', () => {
    it('patches the profile and re-persists the fresh token/user', async () => {
      const auth = useAuthStore()
      api.patch.mockResolvedValue({ data: { token: 'tok-6', user: { username: '1002' } } })
      const result = await auth.updateProfile({ username: '1002' })
      expect(api.patch).toHaveBeenCalledWith('/auth/me', { username: '1002' })
      expect(auth.token).toBe('tok-6')
      expect(auth.user).toEqual({ username: '1002' })
      expect(result).toEqual({ username: '1002' })
    })
  })

  describe('logout', () => {
    it('clears state and localStorage', () => {
      const auth = useAuthStore()
      auth.persist('tok-7', { username: '1001' })
      auth.logout()
      expect(auth.token).toBeNull()
      expect(auth.user).toBeNull()
      expect(localStorage.getItem('heatmap_token')).toBeNull()
      expect(localStorage.getItem('heatmap_user')).toBeNull()
    })
  })
})
