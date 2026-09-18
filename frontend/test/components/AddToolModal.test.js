import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AddToolModal from '../../src/components/AddToolModal.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { post: vi.fn(), patch: vi.fn(), get: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))
import api from '../../src/services/api'

const filterOptions = {
  roles: ['Аналитика', 'Разработка'],
  frameworks: ['Openspec'],
  constraints: ['ПКАП'],
  toolTypes: ['Agent', 'MCP']
}

function mountModal(props = {}, isAdmin = false) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  if (isAdmin) auth.$patch({ token: 't', user: { role: 'ADMIN', fullName: 'Admin' } })
  else auth.$patch({ token: 't', user: { role: 'USER', fullName: 'User' } })
  return mount(AddToolModal, {
    props: { modelValue: true, filterOptions, ...props },
    global: { stubs: { ToolDetailModal: true } }
  })
}

async function fillRequiredFields(wrapper, { withSource = true } = {}) {
  await wrapper.find('#tool-name').setValue('AutoTest-GPT')
  await wrapper.find('#tool-desc').setValue('Генерация тест-кейсов')
  const roleDropdown = wrapper.find('#tool-role')
  await roleDropdown.find('.multiselect-trigger').trigger('click')
  await roleDropdown.findAll('.multiselect-option input[type="checkbox"]')[0].setValue(true)
  if (withSource) {
    await wrapper.find('#tool-source').setValue('https://sc-ci.example.com/repo')
  }
}

describe('AddToolModal', () => {
  beforeEach(() => {
    api.post.mockReset()
    api.patch.mockReset()
  })

  it('is hidden when modelValue is false', () => {
    const wrapper = mountModal({ modelValue: false })
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false)
  })

  it('shows validation errors and does not call the API for empty required fields', async () => {
    const wrapper = mountModal()
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toContain('Заполните все обязательные поля')
    expect(wrapper.find('#tool-name').classes()).toContain('input-invalid')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('requires a sourceLabel for a non-admin user', async () => {
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper, { withSource: false })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('#tool-source').classes()).toContain('input-invalid')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('does not require a sourceLabel for an admin user', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 't1' } })
    const wrapper = mountModal({}, true)
    await fillRequiredFields(wrapper, { withSource: false })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(api.post).toHaveBeenCalled()
  })

  it('rejects a sourceLabel that does not match the corporate host pattern', async () => {
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper, { withSource: false })
    await wrapper.find('#tool-source').setValue('https://example.com/repo')
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toContain('корпоративный сервис')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('submits a valid non-admin form via POST with the expected payload shape', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 't1' } })
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/tools', expect.objectContaining({
      name: 'AutoTest-GPT',
      description: 'Генерация тест-кейсов',
      roles: ['Аналитика'],
      sourceLabel: 'https://sc-ci.example.com/repo'
    }))
    const payload = api.post.mock.calls[0][1]
    expect(payload.stage).toBeUndefined()
    expect(payload.status).toBeUndefined()
    expect(wrapper.emitted('created')).toEqual([[{ id: 't1' }]])
  })

  it('admin submissions include stage/status but not metrics fields when creating', async () => {
    api.post.mockResolvedValueOnce({ data: { id: 't1' } })
    const wrapper = mountModal({}, true)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    const payload = api.post.mock.calls[0][1]
    expect(payload.stage).toBe('ACCESS')
    expect(payload.status).toBe('PENDING')
    expect(payload.downloads).toBeUndefined()
  })

  it('edit mode sends PATCH and includes admin metrics fields', async () => {
    api.patch.mockResolvedValueOnce({ data: { id: 't1' } })
    const editTool = {
      id: 't1',
      name: 'AutoTest-GPT',
      shortDescription: '',
      description: 'desc',
      roles: ['Аналитика'],
      framework: [],
      toolType: '',
      constraints: [],
      sourceLabel: 'https://sc-ci.example.com/repo',
      stage: 'ACCESS',
      status: 'PUBLISHED',
      downloads: 10,
      dau: null,
      efficiencyPct: 50,
      segment: ''
    }
    const wrapper = mountModal({ editTool }, true)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(api.patch).toHaveBeenCalledWith('/tools/t1', expect.objectContaining({
      downloads: 10,
      efficiencyPct: 50
    }))
    expect(wrapper.emitted('updated')).toEqual([[{ id: 't1' }]])
  })

  it('shows a clickable conflict name on a 409 duplicate sourceLabel response', async () => {
    api.post.mockRejectedValueOnce({
      response: { status: 409, data: { conflictToolId: 'other-id', conflictToolName: 'CodeReview-Agent' } }
    })
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('#tool-source').classes()).toContain('input-invalid')
    expect(wrapper.find('.link-btn').text()).toBe('CodeReview-Agent')
  })

  it('shows a generic error message on a non-conflict failure', async () => {
    api.post.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Не удалось отправить заявку')
  })

  it('clicking the duplicate tool name opens its detail card', async () => {
    api.post.mockRejectedValueOnce({
      response: { status: 409, data: { conflictToolId: 'other-id', conflictToolName: 'CodeReview-Agent' } }
    })
    api.get.mockResolvedValueOnce({ data: { id: 'other-id', name: 'CodeReview-Agent' } })
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    await wrapper.find('.link-btn').trigger('click')
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/tools/other-id')
    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    expect(detailModal.props('modelValue')).toBe(true)
    expect(detailModal.props('tool').name).toBe('CodeReview-Agent')
  })

  it('silently does nothing when fetching the duplicate tool card fails', async () => {
    api.post.mockRejectedValueOnce({
      response: { status: 409, data: { conflictToolId: 'other-id', conflictToolName: 'CodeReview-Agent' } }
    })
    api.get.mockRejectedValueOnce(new Error('not found'))
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    await wrapper.find('.link-btn').trigger('click')
    await flushPromises()

    const detailModal = wrapper.findComponent({ name: 'ToolDetailModal' })
    expect(detailModal.props('modelValue')).toBe(false)
  })

  it('shows a resubmit-to-moderation hint when a non-admin edits their published tool', () => {
    const editTool = {
      id: 't1',
      name: 'AutoTest-GPT',
      shortDescription: '',
      description: 'desc',
      roles: ['Аналитика'],
      framework: [],
      toolType: '',
      constraints: [],
      sourceLabel: 'https://sc-ci.example.com/repo',
      stage: 'ACCESS',
      status: 'PUBLISHED',
      downloads: 10,
      dau: null,
      efficiencyPct: 50,
      segment: ''
    }
    const wrapper = mountModal({ editTool }, false)
    expect(wrapper.find('.modal-hint').text()).toBe('Изменения отправят инструмент на повторную модерацию.')
  })

  it('closing the modal resets the error/duplicate state', async () => {
    api.post.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    const wrapper = mountModal({}, false)
    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.find('.error-text').exists()).toBe(true)

    await wrapper.find('.icon-btn').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([false])
  })
})
