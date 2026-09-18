import { mount } from '@vue/test-utils'
import ComboboxInput from '../../src/components/ComboboxInput.vue'

const options = ['Аналитика', 'Разработка', 'Тестирование']

describe('ComboboxInput', () => {
  it('renders the initial modelValue in the input', () => {
    const wrapper = mount(ComboboxInput, {
      props: { modelValue: 'Разработка', options }
    })
    expect(wrapper.find('input').element.value).toBe('Разработка')
  })

  it('does not show the suggestions panel until focused', () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
  })

  it('opens the panel on focus and lists all options when empty', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    await wrapper.find('input').trigger('focus')
    expect(wrapper.find('.combobox-panel').exists()).toBe(true)
    const items = wrapper.findAll('.combobox-option')
    expect(items).toHaveLength(3)
  })

  it('filters options case-insensitively as the user types', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    await wrapper.find('input').trigger('focus')
    await wrapper.find('input').setValue('разр')
    const items = wrapper.findAll('.combobox-option')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('Разработка')
  })

  it('emits update:modelValue with the typed text (free input allowed)', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    await wrapper.find('input').setValue('Своё значение')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['Своё значение'])
  })

  it('shows the "no matches" hint when no option matches the typed text', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    await wrapper.find('input').trigger('focus')
    await wrapper.find('input').setValue('несуществующее')
    expect(wrapper.findAll('.combobox-option')).toHaveLength(0)
    expect(wrapper.find('.combobox-empty').exists()).toBe(true)
  })

  it('picking an option emits it and closes the panel', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    await wrapper.find('input').trigger('focus')
    await wrapper.findAll('.combobox-option')[1].trigger('mousedown')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['Разработка'])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
  })

  it('toggles the panel via the chevron button', async () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options } })
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
    await wrapper.find('.combobox-chevron-btn').trigger('mousedown')
    expect(wrapper.find('.combobox-panel').exists()).toBe(true)
    await wrapper.find('.combobox-chevron-btn').trigger('mousedown')
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
  })

  it('closes the panel on Escape', async () => {
    const wrapper = mount(ComboboxInput, {
      props: { modelValue: '', options },
      attachTo: document.body
    })
    await wrapper.find('input').trigger('focus')
    expect(wrapper.find('.combobox-panel').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('closes the panel on an outside click', async () => {
    const wrapper = mount(ComboboxInput, {
      props: { modelValue: '', options },
      attachTo: document.body
    })
    await wrapper.find('input').trigger('focus')
    expect(wrapper.find('.combobox-panel').exists()).toBe(true)
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.combobox-panel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('applies the invalid styling class when invalid prop is true', () => {
    const wrapper = mount(ComboboxInput, { props: { modelValue: '', options, invalid: true } })
    expect(wrapper.find('.combobox-field').classes()).toContain('input-invalid')
  })
})
