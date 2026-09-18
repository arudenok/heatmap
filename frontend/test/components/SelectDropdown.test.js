import { mount } from '@vue/test-utils'
import SelectDropdown from '../../src/components/SelectDropdown.vue'

const options = [
  { value: 'access', label: 'Access' },
  { value: 'usage', label: 'Usage' }
]

describe('SelectDropdown', () => {
  it('shows the label of the currently selected option', () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: 'usage', options } })
    expect(wrapper.find('.select-dd-summary').text()).toBe('Usage')
    expect(wrapper.find('.select-dd-summary').classes()).not.toContain('select-dd-muted')
  })

  it('falls back to the raw value when it is not found among options', () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: 'unknown-value', options } })
    expect(wrapper.find('.select-dd-summary').text()).toBe('unknown-value')
  })

  it('shows muted placeholder styling when modelValue is empty', () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options } })
    expect(wrapper.find('.select-dd-summary').text()).toBe('')
    expect(wrapper.find('.select-dd-summary').classes()).toContain('select-dd-muted')
  })

  it('opens the panel and lists all options, marking the active one', async () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: 'access', options } })
    await wrapper.find('.select-dd-trigger').trigger('click')
    const items = wrapper.findAll('.select-dd-option')
    expect(items).toHaveLength(2)
    expect(items[0].classes()).toContain('select-dd-option-active')
    expect(items[1].classes()).not.toContain('select-dd-option-active')
  })

  it('choosing an option emits update:modelValue with its value and closes the panel', async () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options } })
    await wrapper.find('.select-dd-trigger').trigger('click')
    await wrapper.findAll('.select-dd-option')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['usage'])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.select-dd-panel').exists()).toBe(false)
  })

  it('shows "Нет вариантов" for an empty options list', async () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options: [] } })
    await wrapper.find('.select-dd-trigger').trigger('click')
    expect(wrapper.find('.select-dd-empty').text()).toBe('Нет вариантов')
  })

  it('does not render the custom-value input unless allowCustom is set', async () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options } })
    await wrapper.find('.select-dd-trigger').trigger('click')
    expect(wrapper.find('.select-dd-custom-input').exists()).toBe(false)
  })

  it('allowCustom: entering a value and pressing Enter selects it and closes the panel', async () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options, allowCustom: true } })
    await wrapper.find('.select-dd-trigger').trigger('click')
    const input = wrapper.find('.select-dd-custom-input')
    await input.setValue('Свой тип')
    await input.trigger('keydown.enter')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['Свой тип'])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.select-dd-panel').exists()).toBe(false)
  })

  it('applies compact styling classes when compact prop is true', () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options, compact: true } })
    expect(wrapper.find('.select-dd-trigger').classes()).toContain('select-dd-compact')
    expect(wrapper.find('.select-dd-trigger').classes()).not.toContain('input')
  })

  it('applies invalid styling when invalid prop is true', () => {
    const wrapper = mount(SelectDropdown, { props: { modelValue: '', options, invalid: true } })
    expect(wrapper.find('.select-dd-trigger').classes()).toContain('input-invalid')
  })

  it('closes on outside click and on Escape', async () => {
    const wrapper = mount(SelectDropdown, {
      props: { modelValue: '', options },
      attachTo: document.body
    })
    await wrapper.find('.select-dd-trigger').trigger('click')
    expect(wrapper.find('.select-dd-panel').exists()).toBe(true)
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.select-dd-panel').exists()).toBe(false)

    await wrapper.find('.select-dd-trigger').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.select-dd-panel').exists()).toBe(false)
    wrapper.unmount()
  })
})
