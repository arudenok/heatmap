import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RatingPrompt from '../../src/components/RatingPrompt.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { post: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))
import api from '../../src/services/api'

const tool = { id: 'tool-1', name: 'AutoTest-GPT' }

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } }
    ]
  })
  return router
}

describe('RatingPrompt', () => {
  let router

  beforeEach(() => {
    setActivePinia(createPinia())
    router = makeRouter()
    api.post.mockReset()
  })

  it('renders nothing when tool prop is null', () => {
    const wrapper = mount(RatingPrompt, { props: { tool: null }, global: { plugins: [router] } })
    expect(wrapper.find('.rating-prompt').exists()).toBe(false)
  })

  it('renders 5 stars and the tool name for an authenticated user', async () => {
    const auth = useAuthStore()
    auth.$patch({ token: 'tok', user: { id: 'u1', role: 'USER', fullName: 'User' } })
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })
    expect(wrapper.find('.rating-prompt-tool').text()).toBe('AutoTest-GPT')
    expect(wrapper.findAll('.star-btn')).toHaveLength(5)
  })

  it('shows the auth-required panel and does NOT call the API when the user is a guest', async () => {
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })
    await wrapper.findAll('.star-btn')[2].trigger('click')
    await flushPromises()
    expect(wrapper.find('.rating-prompt-title').text()).toBe('Оценка доступна только после входа')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('guest clicking "Войти" closes the prompt and navigates to /login', async () => {
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })
    await wrapper.findAll('.star-btn')[0].trigger('click')
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')
    await wrapper.find('.btn-primary').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
    expect(pushSpy).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/' } })
  })

  it('authenticated user clicking a star submits the rating and shows a thank-you state', async () => {
    const auth = useAuthStore()
    auth.$patch({ token: 'tok', user: { id: 'u1', role: 'USER', fullName: 'User' } })
    api.post.mockResolvedValueOnce({ data: {} })
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })

    await wrapper.findAll('.star-btn')[3].trigger('click')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/tools/tool-1/rating', { rating: 4 })
    expect(wrapper.emitted('rated')).toEqual([[4]])
    expect(wrapper.find('.rating-prompt-thanks').exists()).toBe(true)
  })

  it('shows an error message when the rating request fails', async () => {
    const auth = useAuthStore()
    auth.$patch({ token: 'tok', user: { id: 'u1', role: 'USER', fullName: 'User' } })
    api.post.mockRejectedValueOnce({ response: { data: { message: 'Сервер недоступен' } } })
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })

    await wrapper.findAll('.star-btn')[1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Сервер недоступен')
    expect(wrapper.find('.rating-prompt-thanks').exists()).toBe(false)
  })

  it('close button emits close', async () => {
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })
    await wrapper.find('.icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('resets hover/error/done/needsAuth state when the tool prop changes', async () => {
    const wrapper = mount(RatingPrompt, { props: { tool }, global: { plugins: [router] } })
    await wrapper.findAll('.star-btn')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('.rating-prompt-title').text()).toBe('Оценка доступна только после входа')

    await wrapper.setProps({ tool: { id: 'tool-2', name: 'CodeReview-Agent' } })
    expect(wrapper.find('.rating-prompt-title').text()).toBe('Пожалуйста, оцените инструмент')
    expect(wrapper.find('.rating-prompt-tool').text()).toBe('CodeReview-Agent')
  })
})
