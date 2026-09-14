import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initTheme } from './utils/theme'
import './assets/styles.css'

// Применяем тему до монтирования, чтобы не было мигания неправильными цветами.
initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
