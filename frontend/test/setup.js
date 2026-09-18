import { afterEach } from 'vitest'

// jsdom предоставляет реальный localStorage, но между тестами его нужно чистить самим -
// иначе состояние (токен/пользователь/тема) "утекает" из одного теста в другой.
afterEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})
