import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import api, { extractErrorMessage } from '../api'

describe('extractErrorMessage', () => {
  it('returns the fallback when there is no response data', () => {
    expect(extractErrorMessage({})).toBe('Что-то пошло не так, попробуйте ещё раз')
  })

  it('returns a custom fallback when provided', () => {
    expect(extractErrorMessage({}, 'Кастомная ошибка')).toBe('Кастомная ошибка')
  })

  it('joins field errors when present', () => {
    const error = {
      response: { data: { fieldErrors: { name: 'Введите имя', email: 'Некорректный email' } } }
    }
    expect(extractErrorMessage(error)).toBe('Введите имя; Некорректный email')
  })

  it('falls back to message when fieldErrors is empty', () => {
    const error = { response: { data: { fieldErrors: {}, message: 'Ошибка сервера' } } }
    expect(extractErrorMessage(error)).toBe('Ошибка сервера')
  })

  it('returns message when there are no fieldErrors at all', () => {
    const error = { response: { data: { message: 'Не найдено' } } }
    expect(extractErrorMessage(error)).toBe('Не найдено')
  })

  it('returns fallback when data has neither fieldErrors nor message', () => {
    const error = { response: { data: {} } }
    expect(extractErrorMessage(error)).toBe('Что-то пошло не так, попробуйте ещё раз')
  })
})

describe('api interceptors', () => {
  const requestInterceptor = () => api.interceptors.request.handlers[0]
  const responseInterceptor = () => api.interceptors.response.handlers[0]

  beforeEach(() => {
    localStorage.clear()
  })

  it('attaches the Authorization header when a token is stored', () => {
    localStorage.setItem('heatmap_token', 'abc123')
    const config = requestInterceptor().fulfilled({ headers: {} })
    expect(config.headers.Authorization).toBe('Bearer abc123')
  })

  it('does not attach an Authorization header when no token is stored', () => {
    const config = requestInterceptor().fulfilled({ headers: {} })
    expect(config.headers.Authorization).toBeUndefined()
  })

  it('passes successful responses through unchanged', () => {
    const response = { status: 200, data: { ok: true } }
    expect(responseInterceptor().fulfilled(response)).toBe(response)
  })

  describe('on 401 responses', () => {
    let originalLocation

    beforeEach(() => {
      originalLocation = window.location
      Object.defineProperty(window, 'location', {
        writable: true,
        value: { pathname: '/', href: '' }
      })
      localStorage.setItem('heatmap_token', 'abc123')
      localStorage.setItem('heatmap_user', '{"username":"1001"}')
    })

    afterEach(() => {
      Object.defineProperty(window, 'location', { writable: true, value: originalLocation })
    })

    it('clears storage and redirects when the failed request had a token', async () => {
      const error = {
        config: { headers: { Authorization: 'Bearer abc123' } },
        response: { status: 401 }
      }
      await expect(responseInterceptor().rejected(error)).rejects.toBe(error)
      expect(localStorage.getItem('heatmap_token')).toBeNull()
      expect(localStorage.getItem('heatmap_user')).toBeNull()
      expect(window.location.href).toBe('/login')
    })

    it('does not redirect again if already on the login page', async () => {
      window.location.pathname = '/login'
      const error = {
        config: { headers: { Authorization: 'Bearer abc123' } },
        response: { status: 401 }
      }
      await expect(responseInterceptor().rejected(error)).rejects.toBe(error)
      expect(window.location.href).toBe('')
    })

    it('does not clear storage when the failed request had no token (anonymous 401)', async () => {
      const error = { config: { headers: {} }, response: { status: 401 } }
      await expect(responseInterceptor().rejected(error)).rejects.toBe(error)
      expect(localStorage.getItem('heatmap_token')).toBe('abc123')
      expect(window.location.href).toBe('')
    })
  })

  it('rejects non-401 errors without touching storage', async () => {
    localStorage.setItem('heatmap_token', 'abc123')
    const error = { config: { headers: { Authorization: 'Bearer abc123' } }, response: { status: 500 } }
    await expect(responseInterceptor().rejected(error)).rejects.toBe(error)
    expect(localStorage.getItem('heatmap_token')).toBe('abc123')
  })
})
