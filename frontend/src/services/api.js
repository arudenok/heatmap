import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api'
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('heatmap_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Главная страница реестра открыта анонимам, поэтому фоновые запросы
    // (например "мои заявки") у гостя тоже получают 401 - это нормально и не
    // должно никуда перекидывать. Разлогиниваем принудительно только если
    // запрос ушёл с токеном, а сервер всё равно ответил 401 (токен истёк/невалиден).
    const hadToken = Boolean(error.config?.headers?.Authorization)
    if (error.response?.status === 401 && hadToken) {
      localStorage.removeItem('heatmap_token')
      localStorage.removeItem('heatmap_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export function extractErrorMessage(error, fallback = 'Что-то пошло не так, попробуйте ещё раз') {
  const data = error?.response?.data
  if (!data) return fallback
  if (data.fieldErrors && Object.keys(data.fieldErrors).length) {
    return Object.values(data.fieldErrors).join('; ')
  }
  return data.message || fallback
}

export default api
