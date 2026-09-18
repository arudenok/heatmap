import { mount } from '@vue/test-utils'
import FilterBar from '../../src/components/FilterBar.vue'
import MultiSelectDropdown from '../../src/components/MultiSelectDropdown.vue'

const filterOptions = {
  roles: ['Аналитика', 'Разработка'],
  frameworks: ['Openspec', 'Superpowers'],
  constraints: ['ПКАП'],
  toolTypes: ['Agent', 'MCP']
}

function mountBar(props = {}) {
  return mount(FilterBar, {
    props: {
      filterOptions,
      resultCount: 7,
      role: [],
      framework: [],
      constraints: [],
      toolType: [],
      search: '',
      ...props
    }
  })
}

describe('FilterBar', () => {
  it('shows the result count', () => {
    const wrapper = mountBar({ resultCount: 12 })
    expect(wrapper.find('.filter-count').text()).toBe('12 инструмент(а/ов)')
  })

  it('renders one MultiSelectDropdown per filter group with the right options', () => {
    const wrapper = mountBar()
    const dropdowns = wrapper.findAllComponents(MultiSelectDropdown)
    expect(dropdowns).toHaveLength(4)
    expect(dropdowns[0].props('options')).toEqual(filterOptions.roles)
    expect(dropdowns[1].props('options')).toEqual(filterOptions.frameworks)
    expect(dropdowns[2].props('options')).toEqual(filterOptions.constraints)
    expect(dropdowns[3].props('options')).toEqual(filterOptions.toolTypes)
  })

  it('selecting a role option emits update:role with the new array', async () => {
    const wrapper = mountBar()
    const roleDropdown = wrapper.findAllComponents(MultiSelectDropdown)[0]
    await roleDropdown.find('.multiselect-trigger').trigger('click')
    await roleDropdown.findAll('.multiselect-option input[type="checkbox"]')[1].setValue(true)
    expect(wrapper.emitted('update:role').at(-1)).toEqual([['Разработка']])
  })

  it('typing in the search field emits update:search', async () => {
    const wrapper = mountBar()
    await wrapper.find('.filter-search input').setValue('AutoTest')
    expect(wrapper.emitted('update:search').at(-1)).toEqual(['AutoTest'])
  })

  it('clicking "Сбросить" emits reset', async () => {
    const wrapper = mountBar()
    await wrapper.find('.filter-actions .btn').trigger('click')
    expect(wrapper.emitted('reset')).toBeTruthy()
  })

  it('reflects the search prop value in the input', () => {
    const wrapper = mountBar({ search: 'preset text' })
    expect(wrapper.find('.filter-search input').element.value).toBe('preset text')
  })
})
