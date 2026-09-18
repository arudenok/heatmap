import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChangePasswordModal from '../../src/components/ChangePasswordModal.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { patch: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))

function mountModal() {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'User' } })
  const wrapper = mount(ChangePasswordModal, { props: { modelValue: true } })
  return { wrapper, auth }
}

async function fill(wrapper, { current = 'oldpass', next = 'newpass1', confirm = 'newpass1' }) {
  await wrapper.find('#pwd-current').setValue(current)
  await wrapper.find('#pwd-new').setValue(next)
  await wrapper.find('#pwd-confirm').setValue(confirm)
}

describe('ChangePasswordModal', () => {
  it('is hidden when modelValue is false', () => {
    setActivePinia(createPinia())
    const wrapper = mount(ChangePasswordModal, { props: { modelValue: false } })
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false)
  })

  it('requires a current password', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn()
    await fill(wrapper, { current: '' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Введите текущий пароль')
    expect(auth.updateProfile).not.toHaveBeenCalled()
  })

  it('rejects a new password shorter than 6 characters', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn()
    await fill(wrapper, { next: 'abc', confirm: 'abc' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Новый пароль должен быть не короче 6 символов')
    expect(auth.updateProfile).not.toHaveBeenCalled()
  })

  it('rejects a new password identical to the current one', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn()
    await fill(wrapper, { current: 'samepass', next: 'samepass', confirm: 'samepass' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Новый пароль должен отличаться от текущего')
    expect(auth.updateProfile).not.toHaveBeenCalled()
  })

  it('rejects a mismatched confirmation', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn()
    await fill(wrapper, { confirm: 'different1' })
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.find('.error-text').text()).toBe('Новый пароль и подтверждение не совпадают')
    expect(auth.updateProfile).not.toHaveBeenCalled()
  })

  it('submits valid data and shows a success message, clearing the fields', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn().mockResolvedValue({})
    await fill(wrapper, {})
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.updateProfile).toHaveBeenCalledWith({ currentPassword: 'oldpass', newPassword: 'newpass1' })
    expect(wrapper.find('.success-text').text()).toBe('Пароль изменён')
    expect(wrapper.find('#pwd-current').element.value).toBe('')
  })

  it('shows the server error (e.g. wrong current password) on failure', async () => {
    const { wrapper, auth } = mountModal()
    auth.updateProfile = vi.fn().mockRejectedValue({ response: { data: { message: 'Неверный текущий пароль' } } })
    await fill(wrapper, {})
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Неверный текущий пароль')
  })

  it('close button emits update:modelValue false', async () => {
    const { wrapper } = mountModal()
    await wrapper.find('.modal-actions .btn-outline').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})
