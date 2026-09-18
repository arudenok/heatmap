import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from '../../src/views/LoginView.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { post: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<div/>' } },
      { path: '/admin', name: 'admin', component: { template: '<div/>' } }
    ]
  })
}

async function mountView(initialPath = '/login') {
  setActivePinia(createPinia())
  const router = makeRouter()
  router.push(initialPath)
  await router.isReady()
  const wrapper = mount(LoginView, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('LoginView', () => {
  it('submits credentials and redirects to the dashboard on success', async () => {
    const { wrapper, router } = await mountView()
    const auth = useAuthStore()
    auth.login = vi.fn().mockResolvedValue({})
    const replaceSpy = vi.spyOn(router, 'replace')

    await wrapper.find('#login').setValue('1000')
    await wrapper.find('#password').setValue('admin123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledWith('1000', 'admin123')
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'dashboard' })
  })

  it('redirects to the ?redirect= query target when present', async () => {
    const { wrapper, router } = await mountView('/login?redirect=%2Fadmin')
    const auth = useAuthStore()
    auth.login = vi.fn().mockResolvedValue({})
    const replaceSpy = vi.spyOn(router, 'replace')

    await wrapper.find('#login').setValue('1000')
    await wrapper.find('#password').setValue('admin123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(replaceSpy).toHaveBeenCalledWith('/admin')
  })

  it('shows an error message and does not navigate when login fails', async () => {
    const { wrapper, router } = await mountView()
    const auth = useAuthStore()
    auth.login = vi.fn().mockRejectedValue({ response: { data: { message: 'Неверное имя пользователя или пароль' } } })
    const replaceSpy = vi.spyOn(router, 'replace')

    await wrapper.find('#login').setValue('1000')
    await wrapper.find('#password').setValue('wrong')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Неверное имя пользователя или пароль')
    expect(replaceSpy).not.toHaveBeenCalled()
  })

  it('shows a link to the registration page', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.find('.auth-switch a').attributes('href')).toBe('/register')
  })
})
