import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import router from '../index'

describe('router navigation guards', () => {
  beforeEach(async () => {
    localStorage.clear()
    setActivePinia(createPinia())
    await router.push('/')
    await router.isReady()
  })

  it('allows the dashboard for anonymous visitors', async () => {
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  describe('guestOnly routes (/login, /register)', () => {
    it('are reachable when not authenticated', async () => {
      await router.push('/login')
      expect(router.currentRoute.value.name).toBe('login')

      await router.push('/register')
      expect(router.currentRoute.value.name).toBe('register')
    })

    it('redirect an already-authenticated user to the dashboard', async () => {
      const auth = useAuthStore()
      auth.persist('tok', { username: '1001', role: 'USER' })

      await router.push('/login')
      expect(router.currentRoute.value.name).toBe('dashboard')
    })
  })

  describe('requiresAuth routes (/admin)', () => {
    it('redirect an unauthenticated visitor to /login with a redirect query', async () => {
      await router.push('/admin')
      expect(router.currentRoute.value.name).toBe('login')
      expect(router.currentRoute.value.query.redirect).toBe('/admin')
    })

    it('redirect an authenticated non-admin to the dashboard', async () => {
      const auth = useAuthStore()
      auth.persist('tok', { username: '1001', role: 'USER' })

      await router.push('/admin')
      expect(router.currentRoute.value.name).toBe('dashboard')
    })

    it('allow an authenticated admin through', async () => {
      const auth = useAuthStore()
      auth.persist('tok', { username: '1000', role: 'ADMIN' })

      await router.push('/admin')
      expect(router.currentRoute.value.name).toBe('admin')
    })
  })

  describe('unknown routes', () => {
    it('resolve to the not-found route', async () => {
      await router.push('/this-route-does-not-exist')
      expect(router.currentRoute.value.name).toBe('not-found')
    })
  })
})
