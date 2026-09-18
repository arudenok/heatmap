import { mount } from '@vue/test-utils'
import ConfirmDialog from '../../src/components/ConfirmDialog.vue'
import { useConfirm } from '../../src/composables/useConfirm'

describe('ConfirmDialog', () => {
  afterEach(() => {
    // useConfirm state is a module-level singleton - reset it between tests so an
    // open/settled dialog from one test doesn't leak into the next.
    const { confirmState, settle } = useConfirm()
    if (confirmState.open) settle(false)
  })

  it('is hidden when no confirm() call is in flight', () => {
    const wrapper = mount(ConfirmDialog)
    expect(wrapper.find('.confirm-backdrop').exists()).toBe(false)
  })

  it('shows title, message and labels while a confirm() call is pending', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm } = useConfirm()
    confirm('Удалить инструмент?', {
      title: 'Подтвердите удаление',
      confirmLabel: 'Удалить',
      cancelLabel: 'Не удалять'
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.confirm-backdrop').exists()).toBe(true)
    expect(wrapper.find('h3').text()).toBe('Подтвердите удаление')
    expect(wrapper.find('.confirm-message').text()).toBe('Удалить инструмент?')
    const buttons = wrapper.findAll('button')
    expect(buttons[0].text()).toBe('Не удалять')
    expect(buttons[1].text()).toBe('Удалить')
  })

  it('resolves true and closes when the confirm button is clicked', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm } = useConfirm()
    const promise = confirm('Точно?')
    await wrapper.vm.$nextTick()

    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')

    await expect(promise).resolves.toBe(true)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.confirm-backdrop').exists()).toBe(false)
  })

  it('resolves false when the cancel button is clicked', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm } = useConfirm()
    const promise = confirm('Точно?')
    await wrapper.vm.$nextTick()

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')

    await expect(promise).resolves.toBe(false)
  })

  it('resolves false when the backdrop itself is clicked (not the modal content)', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm } = useConfirm()
    const promise = confirm('Точно?')
    await wrapper.vm.$nextTick()

    await wrapper.find('.confirm-backdrop').trigger('click')

    await expect(promise).resolves.toBe(false)
  })

  it('applies the danger icon/button styling by default and swaps to primary when danger=false', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm, settle } = useConfirm()

    confirm('Опасное действие')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.confirm-icon').classes()).toContain('confirm-icon-danger')
    expect(wrapper.findAll('button')[1].classes()).toContain('btn-danger')
    settle(false)
    await wrapper.vm.$nextTick()

    confirm('Обычное действие', { danger: false })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.confirm-icon').classes()).not.toContain('confirm-icon-danger')
    expect(wrapper.findAll('button')[1].classes()).toContain('btn-primary')
    settle(false)
  })

  it('settles false on Escape and true on Enter while open', async () => {
    const wrapper = mount(ConfirmDialog)
    const { confirm } = useConfirm()

    const p1 = confirm('Точно?')
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await expect(p1).resolves.toBe(false)

    await wrapper.vm.$nextTick()
    const p2 = confirm('Точно?')
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await expect(p2).resolves.toBe(true)

    wrapper.unmount()
  })

  it('ignores keydown events when no dialog is open', () => {
    const wrapper = mount(ConfirmDialog)
    // Should not throw even though confirmState.open is false.
    expect(() =>
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    ).not.toThrow()
    wrapper.unmount()
  })
})
