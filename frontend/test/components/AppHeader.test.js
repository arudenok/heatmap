import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import AppHeader from '../../src/components/AppHeader.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  extractErrorMessage: (e, fallback) => fallback
}))
import api from '../../src/services/api'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/admin', name: 'admin', component: { template: '<div/>' } }
    ]
  })
}

function mountHeader() {
  const router = makeRouter()
  const wrapper = mount(AppHeader, {
    global: {
      plugins: [router],
      stubs: { EditProfileModal: true, MyDownloadsModal: true, ToolNotesModal: true }
    }
  })
  return { wrapper, router }
}

describe('AppHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    window.matchMedia = vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn() })
    api.get.mockReset()
    api.post.mockReset()
    // AppHeader опрашивает /auth/me при каждом переходе isAuthenticated=false->true
    // (refreshOwnRole) - по умолчанию отклоняем этот конкретный запрос (он молча
    // проглатывается компонентом), чтобы он не перезаписывал auth.user данными
    // отовсюду-заглушки "[]" и не ломал патч, сделанный тестом напрямую через auth.$patch.
    api.get.mockImplementation((url) => {
      if (url === '/auth/me') return Promise.reject(new Error('not mocked in this test'))
      return Promise.resolve({ data: [] })
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a login link and no user menu for a guest', async () => {
    const { wrapper } = mountHeader()
    await flushPromises()
    expect(wrapper.find('.header-guest a').text()).toBe('Войти')
    expect(wrapper.find('.header-user').exists()).toBe(false)
  })

  it('shows the display name and no admin link for an authenticated non-admin user', async () => {
    const { wrapper } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'Иванов И.И.' } })
    await flushPromises()

    expect(wrapper.find('.user-name').text()).toBe('Иванов И.И.')
    expect(wrapper.find('.admin-toggle').exists()).toBe(false)
  })

  it('shows the admin link for an admin user', async () => {
    const { wrapper } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'ADMIN', fullName: 'Admin' } })
    await flushPromises()

    expect(wrapper.find('.admin-toggle').exists()).toBe(true)
  })

  it('shows the pending-moderation badge on the admin link when count > 0', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/admin/tools/pending') return Promise.resolve({ data: [{}, {}, {}] })
      if (url === '/auth/me') return Promise.reject(new Error('not mocked in this test'))
      return Promise.resolve({ data: [] })
    })
    const { wrapper } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'ADMIN', fullName: 'Admin' } })
    await flushPromises()

    expect(wrapper.find('.admin-toggle .icon-badge').text()).toBe('3')
  })

  it('opens the user menu on click and closes it after choosing "Выйти"', async () => {
    const { wrapper, router } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'User' } })
    await flushPromises()

    await wrapper.find('.header-user').trigger('click')
    expect(wrapper.find('.user-menu').exists()).toBe(true)

    const replaceSpy = vi.spyOn(router, 'replace')
    const logoutBtn = wrapper.findAll('.user-menu-item').at(-1)
    expect(logoutBtn.text()).toContain('Выйти')
    await logoutBtn.trigger('click')

    expect(auth.isAuthenticated).toBe(false)
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'dashboard' })
  })

  it('toggles the notifications dropdown and loads notifications on open', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/notifications') {
        return Promise.resolve({
          data: [{ id: 'n1', type: 'NEW_SUBMISSION', message: 'Новая заявка', read: false, createdAt: new Date().toISOString() }]
        })
      }
      if (url === '/notifications/unread-count') return Promise.resolve({ data: { count: 1 } })
      return Promise.resolve({ data: [] })
    })
    const { wrapper } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'User' } })
    await flushPromises()

    expect(wrapper.find('.notif-toggle .icon-badge').text()).toBe('1')
    await wrapper.find('.notif-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.find('.notif-dropdown').exists()).toBe(true)
    expect(wrapper.find('.notif-item').exists()).toBe(true)
    expect(wrapper.find('.notif-item').classes()).toContain('unread')
  })

  it('clicking an unread notification marks it read and decrements the unread count', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/notifications') {
        return Promise.resolve({
          data: [{ id: 'n1', type: 'SUBMISSION_APPROVED', message: 'Одобрено', read: false, createdAt: new Date().toISOString() }]
        })
      }
      if (url === '/notifications/unread-count') return Promise.resolve({ data: { count: 1 } })
      return Promise.resolve({ data: [] })
    })
    api.post.mockResolvedValue({ data: {} })
    const { wrapper } = mountHeader()
    const auth = useAuthStore()
    auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'User' } })
    await flushPromises()

    await wrapper.find('.notif-toggle').trigger('click')
    await flushPromises()
    await wrapper.find('.notif-item').trigger('click')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/notifications/n1/read')
    expect(wrapper.find('.notif-toggle .icon-badge').exists()).toBe(false)
  })

  it('toggles the theme when the theme button is clicked', async () => {
    const { wrapper } = mountHeader()
    await flushPromises()
    const before = document.documentElement.getAttribute('data-theme')
    await wrapper.find('.theme-toggle:not(.admin-toggle):not(.notif-toggle)').trigger('click')
    const after = document.documentElement.getAttribute('data-theme')
    expect(after).not.toBe(before)
  })
})
