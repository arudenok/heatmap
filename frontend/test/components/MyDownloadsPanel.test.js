import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyDownloadsPanel from '../../src/components/MyDownloadsPanel.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))
import api from '../../src/services/api'

function authenticate() {
  const auth = useAuthStore()
  auth.$patch({ token: 'tok', user: { id: 'u1', role: 'USER', fullName: 'User' } })
  return auth
}

describe('MyDownloadsPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    api.get.mockReset()
    api.post.mockReset()
  })

  it('renders nothing for a guest (not authenticated, nothing to load)', async () => {
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()
    expect(wrapper.find('.my-downloads').exists()).toBe(false)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('loads and renders downloaded tools for an authenticated user', async () => {
    authenticate()
    api.get.mockResolvedValue({
      data: [
        { id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 0 },
        { id: 't2', name: 'DocAssist', stage: 'HABIT', myRating: 4 }
      ]
    })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/tools/downloaded')
    const rows = wrapper.findAll('.my-downloads-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.my-downloads-name').text()).toContain('AutoTest-GPT')
    expect(rows[0].find('.stage-badge').text()).toBe('Access')
    expect(rows[0].find('.my-downloads-rating-note').text()).toBe('оцените инструмент')
    expect(rows[1].find('.stage-badge').text()).toBe('Habit')
    expect(rows[1].find('.my-downloads-rating-note').text()).toBe('ваша оценка: 4')
  })

  it('shows an error message when loading fails', async () => {
    authenticate()
    api.get.mockRejectedValue({ response: { data: { message: 'Сбой сети' } } })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()
    expect(wrapper.find('.error-text').text()).toBe('Сбой сети')
  })

  it('does not render the panel when authenticated but the list is empty and not loading', async () => {
    authenticate()
    api.get.mockResolvedValue({ data: [] })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()
    expect(wrapper.find('.my-downloads').exists()).toBe(false)
  })

  it('clicking a star rates the tool and merges the response into it', async () => {
    authenticate()
    api.get.mockResolvedValue({ data: [{ id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 0 }] })
    api.post.mockResolvedValue({ data: { id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 5 } })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()

    const stars = wrapper.findAll('.star-btn')
    await stars[4].trigger('click')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/tools/t1/rating', { rating: 5 })
    expect(wrapper.find('.my-downloads-rating-note').text()).toBe('ваша оценка: 5')
  })

  it('shows an error and keeps the previous rating when rating fails', async () => {
    authenticate()
    api.get.mockResolvedValue({ data: [{ id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 2 }] })
    api.post.mockRejectedValue({ response: { data: { message: 'Не удалось сохранить' } } })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()

    await wrapper.findAll('.star-btn')[3].trigger('click')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Не удалось сохранить')
    expect(wrapper.find('.my-downloads-rating-note').text()).toBe('ваша оценка: 2')
  })

  it('ignores further star clicks while a rating request is in flight', async () => {
    authenticate()
    api.get.mockResolvedValue({ data: [{ id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 0 }] })
    let resolvePost
    api.post.mockReturnValue(new Promise((resolve) => { resolvePost = resolve }))
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()

    const stars = wrapper.findAll('.star-btn')
    await stars[0].trigger('click')
    await stars[1].trigger('click')
    expect(api.post).toHaveBeenCalledTimes(1)

    resolvePost({ data: { id: 't1', name: 'AutoTest-GPT', stage: 'ACCESS', myRating: 1 } })
    await flushPromises()
  })

  it('exposes a load() method for the parent to trigger a refresh', async () => {
    authenticate()
    api.get.mockResolvedValue({ data: [] })
    const wrapper = mount(MyDownloadsPanel)
    await flushPromises()
    expect(api.get).toHaveBeenCalledTimes(1)

    await wrapper.vm.load()
    expect(api.get).toHaveBeenCalledTimes(2)
  })
})
