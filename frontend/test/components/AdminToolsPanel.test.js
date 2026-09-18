import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminToolsPanel from '../../src/components/AdminToolsPanel.vue'

vi.mock('../../src/services/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))
import api from '../../src/services/api'

const tools = [
  { id: 't1', name: 'AutoTest-GPT', roles: ['Тестирование'], framework: [], ownerName: 'Иванов И.И.', isTop: true, notesCount: 2 },
  { id: 't2', name: 'DocAssist', roles: ['Аналитика'], framework: ['Openspec'], ownerName: 'Петрова А.С.', isTop: false, notesCount: 0 }
]

function mountPanel() {
  setActivePinia(createPinia())
  return mount(AdminToolsPanel, {
    global: { stubs: { AddToolModal: true, ToolNotesModal: true, ToolDetailModal: true } }
  })
}

describe('AdminToolsPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    api.get.mockReset()
    api.post.mockReset()
    api.get.mockImplementation((url) => {
      if (url === '/tools/filter-options') {
        return Promise.resolve({ data: { roles: [], frameworks: [], constraints: [] } })
      }
      return Promise.resolve({ data: tools })
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads tools and filter options on mount', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/tools', { params: { tab: 'ALL', search: undefined, sort: 'CREATED_AT' } })
    expect(api.get).toHaveBeenCalledWith('/tools/filter-options')
    expect(wrapper.findAll('.tools-row')).toHaveLength(2)
    expect(wrapper.find('.badge-info').text()).toBe('2')
  })

  it('shows an empty state when there are no tools', async () => {
    api.get.mockImplementation((url) =>
      url === '/tools/filter-options'
        ? Promise.resolve({ data: { roles: [], frameworks: [], constraints: [] } })
        : Promise.resolve({ data: [] })
    )
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.empty-state').exists()).toBe(true)
  })

  it('shows an error message when loading fails', async () => {
    api.get.mockImplementation((url) =>
      url === '/tools/filter-options'
        ? Promise.resolve({ data: { roles: [], frameworks: [], constraints: [] } })
        : Promise.reject({ response: { data: { message: 'Сервер недоступен' } } })
    )
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.error-text').text()).toBe('Сервер недоступен')
  })

  it('filters to notes-only tools when the toggle is clicked', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('.tools-row')).toHaveLength(2)

    await wrapper.find('.admin-panel-actions .btn.btn-ghost').trigger('click')
    expect(wrapper.findAll('.tools-row')).toHaveLength(1)
    expect(wrapper.find('.tools-title').text()).toContain('AutoTest-GPT')
  })

  it('debounces search input before reloading', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    api.get.mockClear()

    await wrapper.find('.search-input').setValue('Auto')
    expect(api.get).not.toHaveBeenCalled()

    vi.advanceTimersByTime(250)
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/tools', { params: { tab: 'ALL', search: 'Auto', sort: 'CREATED_AT' } })
  })

  it('the refresh button reloads the list', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    api.get.mockClear()

    await wrapper.findAll('.admin-panel-actions .btn')[1].trigger('click')
    await flushPromises()
    expect(api.get).toHaveBeenCalledWith('/tools', expect.objectContaining({ params: expect.any(Object) }))
  })

  it('clicking a tool row registers a view and opens the detail modal', async () => {
    api.post.mockResolvedValue({ data: { ...tools[0], views: 1 } })
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.findAll('.tools-row')[0].trigger('click')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/tools/t1/view')
    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    expect(detailModal.props('modelValue')).toBe(true)
    expect(detailModal.props('tool').id).toBe('t1')
  })

  it('opens the detail modal even when the view-count request fails', async () => {
    api.post.mockRejectedValue(new Error('network error'))
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.findAll('.tools-row')[0].trigger('click')
    await flushPromises()

    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    expect(detailModal.props('modelValue')).toBe(true)
  })

  it('clicking the "Заметки" button opens the notes modal for that tool without opening the detail modal', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('.notes-action-btn').trigger('click')

    const notesModal = wrapper.findComponent({ name: 'ToolNotesModal' })
    expect(notesModal.props('modelValue')).toBe(true)
    expect(notesModal.props('tool').id).toBe('t1')
    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    expect(detailModal.props('modelValue')).toBe(false)
  })

  it('emits edit from the detail modal into the AddToolModal edit mode', async () => {
    api.post.mockResolvedValue({ data: tools[0] })
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.tools-row')[0].trigger('click')
    await flushPromises()

    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    await detailModal.vm.$emit('edit', tools[0])

    expect(detailModal.props('modelValue')).toBe(false)
    const addModal = wrapper.findComponent({ name: 'AddToolModal' })
    expect(addModal.props('modelValue')).toBe(true)
    expect(addModal.props('editTool').id).toBe('t1')
  })

  it('reloads the list after the AddToolModal reports an update', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    api.get.mockClear()

    const addModal = wrapper.findComponent({ name: 'AddToolModal' })
    await addModal.vm.$emit('updated')
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/tools', expect.objectContaining({ params: expect.any(Object) }))
  })

  it('removes the tool from the list when the detail modal reports it archived', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('.tools-row')).toHaveLength(2)

    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    await detailModal.vm.$emit('archived', tools[0])

    expect(wrapper.findAll('.tools-row')).toHaveLength(1)
    expect(wrapper.find('.tools-title').text()).toContain('DocAssist')
  })

  it('exposes a refresh() method for the parent to trigger a reload', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    api.get.mockClear()

    await wrapper.vm.refresh()
    expect(api.get).toHaveBeenCalledWith('/tools', expect.objectContaining({ params: expect.any(Object) }))
  })
})
