import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ToolCard from '../../src/components/ToolCard.vue'
import { useAuthStore } from '../../src/stores/auth'

const baseTool = {
  id: 't1',
  name: 'AutoTest-GPT',
  description: 'Генерация тест-кейсов на основе спецификаций и пользовательских сценариев для регрессионного покрытия',
  shortDescription: '',
  stage: 'ACCESS',
  status: 'PUBLISHED',
  roles: ['Тестирование'],
  framework: ['Openspec'],
  constraints: [],
  toolType: 'Agent',
  ownerName: 'Иванов И.И.',
  downloads: 124,
  dau: null,
  efficiencyPct: 92,
  isTop: false,
  views: 340,
  avgRating: 4.2,
  ratingsCount: 5,
  canManage: false,
  notesCount: 0
}

function mountCard(props = {}, isAdmin = false) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  if (isAdmin) auth.$patch({ token: 't', user: { role: 'ADMIN', fullName: 'Admin' } })
  return mount(ToolCard, {
    props: { tool: baseTool, ...props },
    global: { stubs: { ToolNotesModal: true } }
  })
}

describe('ToolCard', () => {
  it('renders the tool name and owner', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.tool-title').text()).toContain('AutoTest-GPT')
    expect(wrapper.text()).toContain('Иванов И.И.')
  })

  it('shows the short description when present, otherwise a truncated full description', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.tool-desc-text').text()).toBe(baseTool.description.slice(0, 100).trimEnd() + '…')

    const withShort = mountCard({ tool: { ...baseTool, shortDescription: 'Короткое описание' } })
    expect(withShort.find('.tool-desc-text').text()).toBe('Короткое описание')
  })

  it('does not truncate a description at or under the 100-char limit', () => {
    const shortDesc = 'Короткий текст'
    const wrapper = mountCard({ tool: { ...baseTool, shortDescription: '', description: shortDesc } })
    expect(wrapper.find('.tool-desc-text').text()).toBe(shortDesc)
  })

  it('shows the "Топ" badge only when isTop is true', () => {
    const notTop = mountCard()
    expect(notTop.text()).not.toContain('Топ')
    const top = mountCard({ tool: { ...baseTool, isTop: true } })
    expect(top.text()).toContain('Топ')
  })

  it('shows the status badge only when showStatus is true', () => {
    const hidden = mountCard()
    expect(hidden.text()).not.toContain('Опубликован')
    const shown = mountCard({ showStatus: true })
    expect(shown.text()).toContain('Опубликован')
  })

  it('shows the ratings chip only when ratingsCount is non-zero', () => {
    const withRating = mountCard()
    expect(withRating.find('.rating-chip').exists()).toBe(true)
    expect(withRating.find('.rating-chip').text()).toContain('4.2')

    const withoutRating = mountCard({ tool: { ...baseTool, ratingsCount: 0 } })
    expect(withoutRating.find('.rating-chip').exists()).toBe(false)
  })

  it('shows DAU as the metric when dau is set, otherwise downloads', () => {
    const noDau = mountCard()
    expect(noDau.find('.metric-lbl').text()).toBe('скачиваний')
    expect(noDau.find('.metric-num').text()).toBe('124')

    const withDau = mountCard({ tool: { ...baseTool, dau: 1200 } })
    expect(withDau.find('.metric-lbl').text()).toBe('DAU')
    expect(withDau.find('.metric-num').text()).toBe('1.2k')
  })

  it('formats large numbers compactly with a "k" suffix', () => {
    const wrapper = mountCard({ tool: { ...baseTool, dau: 3000 } })
    expect(wrapper.find('.metric-num').text()).toBe('3k')
  })

  it('shows the admin-only notes chip only for admins', () => {
    const asUser = mountCard({}, false)
    expect(asUser.find('.notes-chip').exists()).toBe(false)

    const asAdmin = mountCard({ tool: { ...baseTool, notesCount: 3 } }, true)
    expect(asAdmin.find('.notes-chip').exists()).toBe(true)
    expect(asAdmin.find('.notes-chip').text()).toContain('3')
    expect(asAdmin.find('.notes-chip').classes()).toContain('notes-chip-active')
  })

  it('emits "view" with the tool when the card is clicked', async () => {
    const wrapper = mountCard()
    await wrapper.find('.tool-card').trigger('click')
    expect(wrapper.emitted('view')).toEqual([[baseTool]])
  })

  it('clicking the admin notes chip does not also emit "view" (click.stop)', async () => {
    const wrapper = mountCard({}, true)
    await wrapper.find('.notes-chip').trigger('click')
    expect(wrapper.emitted('view')).toBeUndefined()
  })

  it('renders role/framework/toolType/constraint tags', () => {
    const wrapper = mountCard({
      tool: { ...baseTool, roles: ['Тестирование', 'Разработка'], constraints: ['ПКАП'] }
    })
    const tags = wrapper.findAll('.tag').map((t) => t.text())
    expect(tags).toEqual(expect.arrayContaining(['Тестирование', 'Разработка', 'Openspec', 'Agent', 'ПКАП']))
  })
})
