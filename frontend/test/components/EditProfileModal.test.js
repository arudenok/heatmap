import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EditProfileModal from '../../src/components/EditProfileModal.vue'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/api', () => ({
  default: { patch: vi.fn(), get: vi.fn(), post: vi.fn() },
  extractErrorMessage: (e, fallback) => e?.response?.data?.message || fallback
}))

// EditProfileModal предзаполняет форму через watch(open, ...) БЕЗ immediate: true -
// он срабатывает только на переход false -> true (см. EditProfileModal.vue), что и
// происходит в реальном приложении (AppHeader всегда монтирует модалку с open=false
// и только потом открывает её). Монтировать сразу с modelValue=true в обход этого
// перехода - значит не воспроизводить реальный сценарий и не увидеть предзаполнение.
async function mountModal(open = true) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.$patch({ token: 't', user: { id: '1', role: 'USER', fullName: 'Иванов И.И.', username: '1001' } })
  const wrapper = mount(EditProfileModal, {
    props: { modelValue: false },
    global: { stubs: { ChangePasswordModal: true } }
  })
  if (open) {
    await wrapper.setProps({ modelValue: true })
  }
  return { wrapper, auth }
}

describe('EditProfileModal', () => {
  it('is hidden when modelValue is false', async () => {
    const { wrapper } = await mountModal(false)
    expect(wrapper.find('.modal-backdrop').exists()).toBe(false)
  })

  it('prefills the form with the current user data when opened', async () => {
    const { wrapper } = await mountModal(true)
    expect(wrapper.find('#profile-name').element.value).toBe('Иванов И.И.')
    expect(wrapper.find('#profile-username').element.value).toBe('1001')
  })

  it('submitting calls auth.updateProfile with trimmed values and shows a success message', async () => {
    const { wrapper, auth } = await mountModal(true)
    auth.updateProfile = vi.fn().mockResolvedValue({})
    await wrapper.find('#profile-name').setValue('  Новое Имя  ')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.updateProfile).toHaveBeenCalledWith({ fullName: 'Новое Имя', username: '1001' })
    expect(wrapper.find('.success-text').text()).toBe('Профиль обновлён')
  })

  it('shows an error message when updateProfile rejects', async () => {
    const { wrapper, auth } = await mountModal(true)
    auth.updateProfile = vi.fn().mockRejectedValue({ response: { data: { message: 'Логин уже занят' } } })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-text').text()).toBe('Логин уже занят')
    expect(wrapper.find('.success-text').exists()).toBe(false)
  })

  it('close button emits update:modelValue false', async () => {
    const { wrapper } = await mountModal(true)
    await wrapper.find('.modal-actions .btn-outline').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('"Сменить пароль" flips the nested ChangePasswordModal open', async () => {
    const { wrapper } = await mountModal(true)
    const stubBefore = wrapper.find('change-password-modal-stub')
    expect(stubBefore.exists()).toBe(true)
    expect(stubBefore.attributes('modelvalue')).toBe('false')
    await wrapper.find('.link-btn').trigger('click')
    const stubAfter = wrapper.find('change-password-modal-stub')
    expect(stubAfter.attributes('modelvalue')).toBe('true')
  })
})
