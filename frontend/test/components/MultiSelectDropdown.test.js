import { mount } from '@vue/test-utils'
import MultiSelectDropdown from '../../src/components/MultiSelectDropdown.vue'

const options = ['Аналитика', 'Разработка', 'Тестирование']

describe('MultiSelectDropdown', () => {
  it('shows the allLabel when nothing selected', () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: [], options, allLabel: 'Все роли' }
    })
    expect(wrapper.find('.multiselect-summary').text()).toBe('Все роли')
  })

  it('shows "Обязательное поле" instead of allLabel when invalid and empty', () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: [], options, invalid: true }
    })
    expect(wrapper.find('.multiselect-summary').text()).toBe('Обязательное поле')
  })

  it('shows the single selected value directly, or a count for multiple', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: ['Разработка'], options }
    })
    expect(wrapper.find('.multiselect-summary').text()).toBe('Разработка')

    await wrapper.setProps({ modelValue: ['Разработка', 'Тестирование'] })
    expect(wrapper.find('.multiselect-summary').text()).toBe('Выбрано: 2')
  })

  it('opens/closes the panel on trigger click and lists all options', async () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options } })
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-panel').exists()).toBe(true)
    expect(wrapper.findAll('.multiselect-option')).toHaveLength(3)
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)
  })

  it('checking an option emits it added to modelValue', async () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options } })
    await wrapper.find('.multiselect-trigger').trigger('click')
    const checkbox = wrapper.findAll('.multiselect-option input[type="checkbox"]')[1]
    await checkbox.setValue(true)
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([['Разработка']])
  })

  it('unchecking a selected option removes it from modelValue', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: ['Аналитика', 'Разработка'], options }
    })
    await wrapper.find('.multiselect-trigger').trigger('click')
    const checkboxes = wrapper.findAll('.multiselect-option input[type="checkbox"]')
    expect(checkboxes[0].element.checked).toBe(true)
    expect(checkboxes[1].element.checked).toBe(true)
    await checkboxes[0].setValue(false)
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([['Разработка']])
  })

  it('clear button resets selection to an empty array and does not toggle the panel', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: ['Аналитика'], options }
    })
    await wrapper.find('.multiselect-clear').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([[]])
    // clearAll calls event.stopPropagation() so the trigger's own click-to-open should not fire
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)
  })

  it('does not render a custom-value input when allowCustom is false', async () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options, allowCustom: false } })
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-custom-input').exists()).toBe(false)
  })

  it('allowCustom: typing a free value and pressing Enter adds it as a chip and to modelValue', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: [], options, allowCustom: true }
    })
    await wrapper.find('.multiselect-trigger').trigger('click')
    const input = wrapper.find('.multiselect-custom-input')
    await input.setValue('Дизайн-система')
    await input.trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([['Дизайн-система']])
    await wrapper.setProps({ modelValue: ['Дизайн-система'] })
    expect(wrapper.find('.multiselect-chip').text()).toContain('Дизайн-система')
  })

  it('allowCustom: the add button is disabled while the custom input is blank', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: [], options, allowCustom: true }
    })
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-custom-add').attributes('disabled')).toBeDefined()
    await wrapper.find('.multiselect-custom-input').setValue('X')
    expect(wrapper.find('.multiselect-custom-add').attributes('disabled')).toBeUndefined()
  })

  it('allowCustom: removing a custom chip emits modelValue without it', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: ['Дизайн-система'], options, allowCustom: true }
    })
    await wrapper.find('.multiselect-trigger').trigger('click')
    await wrapper.find('.multiselect-chip span[role="button"]').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([[]])
  })

  it('shows "Нет вариантов" when the options list is empty', async () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options: [] } })
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-empty').text()).toBe('Нет вариантов')
  })

  it('the "Применить" button closes the panel', async () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options } })
    await wrapper.find('.multiselect-trigger').trigger('click')
    await wrapper.find('.multiselect-apply').trigger('click')
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)
  })

  it('closes on outside click and on Escape', async () => {
    const wrapper = mount(MultiSelectDropdown, {
      props: { modelValue: [], options },
      attachTo: document.body
    })
    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-panel').exists()).toBe(true)
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)

    await wrapper.find('.multiselect-trigger').trigger('click')
    expect(wrapper.find('.multiselect-panel').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.multiselect-panel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('applies invalid styling to the trigger when invalid prop is true', () => {
    const wrapper = mount(MultiSelectDropdown, { props: { modelValue: [], options, invalid: true } })
    expect(wrapper.find('.multiselect-trigger').classes()).toContain('input-invalid')
  })
})
