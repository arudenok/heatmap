// Управление светлой/тёмной темой: автоопределение по системным настройкам ОС
// плюс возможность ручного переключения, которая сохраняется в localStorage.

const STORAGE_KEY = 'heatmap_theme'

export function getStoredTheme() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

export function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

// Тема, которая реально отображается сейчас: явный выбор пользователя,
// а если его нет - системная настройка ОС.
export function getEffectiveTheme() {
  return getStoredTheme() || (systemPrefersDark() ? 'dark' : 'light')
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme)
}

// Вызывается один раз при старте приложения - до монтирования,
// чтобы не было "мигания" неправильной темой при загрузке.
export function initTheme() {
  applyTheme(getEffectiveTheme())

  // Если пользователь не выбирал тему вручную - следим за изменением
  // системной темы "на лету" (например, включили тёмный режим в macOS).
  if (typeof window !== 'undefined' && window.matchMedia) {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener('change', (e) => {
      if (!getStoredTheme()) {
        applyTheme(e.matches ? 'dark' : 'light')
      }
    })
  }
}

export function toggleTheme() {
  const next = getEffectiveTheme() === 'dark' ? 'light' : 'dark'
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // localStorage недоступен (приватный режим и т.п.) - тема просто не сохранится между визитами
  }
  applyTheme(next)
  return next
}
