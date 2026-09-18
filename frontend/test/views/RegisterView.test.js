import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RegisterView from '../../src/views/RegisterView.vue'
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
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/register', name: 'register', component: RegisterView }
    ]
  })
}

async function mountView() {
  setActivePinia(createPinia())
  const router = makeRouter()
  router.push('/register')
  await router.isReady()
  const wrapper = mount(RegisterView, { global: { plugins: [router] } })
  return { wrapper, router }
}

async function fill(wrapper, { fullName = 'Новый Пользователь', username = '2000', password = 'pass123', confirm = 'pass123' }) {
  await wrapper.find('#fullName').setValue(fullName)
  await wrapper.find('#username').setValue(username)
  await wrapper.find('#password').setValue(password)
  await wrapper.find('#confirmPassword').setValue(confirm)
}

describe('RegisterView', () => {
  it('rejects a fullName shorter than 3 characters (client-side, before hitting the API)', async () => {
    const { wrapper } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn()
    await fill(wrapper, { fullName: 'Ив' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('ФИО должно быть не короче 3 символов')
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('rejects a username shorter than 3 characters', async () => {
    const { wrapper } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn()
    await fill(wrapper, { username: '12' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Логин Сигма должен быть не короче 3 символов')
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('rejects a password shorter than 6 characters', async () => {
    const { wrapper } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn()
    await fill(wrapper, { password: '123', confirm: '123' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Пароль должен быть не короче 6 символов')
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('rejects a mismatched password confirmation', async () => {
    const { wrapper } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn()
    await fill(wrapper, { confirm: 'different1' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Пароль и подтверждение не совпадают')
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('submits without confirmPassword and redirects to dashboard on success', async () => {
    const { wrapper, router } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn().mockResolvedValue({})
    const replaceSpy = vi.spyOn(router, 'replace')

    await fill(wrapper, {})
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Новый Пользователь',
      username: '2000',
      password: 'pass123'
    })
    expect(replaceSpy).toHaveBeenCalledWith({ name: 'dashboard' })
  })

  it('shows a server error (e.g. duplicate username) and does not navigate', async () => {
    const { wrapper, router } = await mountView()
    const auth = useAuthStore()
    auth.register = vi.fn().mockRejectedValue({
      response: { data: { message: 'Пользователь с таким логином Сигма уже зарегистрирован' } }
    })
    const replaceSpy = vi.spyOn(router, 'replace')

    await fill(wrapper, {})
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Пользователь с таким логином Сигма уже зарегистрирован')
    expect(replaceSpy).not.toHaveBeenCalled()
  })
})
