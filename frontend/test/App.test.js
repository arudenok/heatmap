import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../src/App.vue'

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'dashboard', component: { template: '<div class="dashboard-stub"/>' } }]
  })
  return router
}

describe('App', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the current route view and the global confirm dialog', async () => {
    const router = makeRouter()
    const wrapper = mount(App, { global: { plugins: [router] } })
    await router.isReady()

    expect(wrapper.find('.dashboard-stub').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ConfirmDialog' }).exists()).toBe(true)
  })
})
