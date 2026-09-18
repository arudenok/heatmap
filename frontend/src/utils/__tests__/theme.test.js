import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getStoredTheme,
  systemPrefersDark,
  getEffectiveTheme,
  applyTheme,
  initTheme,
  toggleTheme
} from '../theme'

function mockMatchMedia(matches) {
  const listeners = []
  const media = {
    matches,
    addEventListener: vi.fn((event, cb) => listeners.push(cb)),
    removeEventListener: vi.fn()
  }
  window.matchMedia = vi.fn(() => media)
  return { media, listeners }
}

describe('theme utils', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  describe('getStoredTheme', () => {
    it('returns null when nothing stored', () => {
      expect(getStoredTheme()).toBeNull()
    })

    it('returns "light" or "dark" when validly stored', () => {
      localStorage.setItem('heatmap_theme', 'dark')
      expect(getStoredTheme()).toBe('dark')
      localStorage.setItem('heatmap_theme', 'light')
      expect(getStoredTheme()).toBe('light')
    })

    it('returns null for an invalid stored value', () => {
      localStorage.setItem('heatmap_theme', 'purple')
      expect(getStoredTheme()).toBeNull()
    })

    it('returns null when localStorage access throws', () => {
      const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked')
      })
      expect(getStoredTheme()).toBeNull()
      spy.mockRestore()
    })
  })

  describe('systemPrefersDark', () => {
    it('returns matchMedia result when available', () => {
      mockMatchMedia(true)
      expect(systemPrefersDark()).toBe(true)
      mockMatchMedia(false)
      expect(systemPrefersDark()).toBe(false)
    })

    it('returns false when matchMedia is unavailable', () => {
      const original = window.matchMedia
      // @ts-ignore
      delete window.matchMedia
      expect(systemPrefersDark()).toBe(false)
      window.matchMedia = original
    })
  })

  describe('getEffectiveTheme', () => {
    it('prefers the explicitly stored theme over system preference', () => {
      mockMatchMedia(true)
      localStorage.setItem('heatmap_theme', 'light')
      expect(getEffectiveTheme()).toBe('light')
    })

    it('falls back to system preference when nothing stored', () => {
      mockMatchMedia(true)
      expect(getEffectiveTheme()).toBe('dark')
      mockMatchMedia(false)
      expect(getEffectiveTheme()).toBe('light')
    })
  })

  describe('applyTheme', () => {
    it('sets data-theme attribute on the html element', () => {
      applyTheme('dark')
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })
  })

  describe('initTheme', () => {
    it('applies the effective theme on init', () => {
      mockMatchMedia(true)
      initTheme()
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })

    it('registers a system-theme-change listener that only applies when user has not chosen', () => {
      const { listeners } = mockMatchMedia(false)
      initTheme()
      expect(document.documentElement.getAttribute('data-theme')).toBe('light')

      // Система переключилась на тёмную, пользователь тему вручную не выбирал - должно примениться.
      listeners[0]({ matches: true })
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })

    it('does not override an explicit user choice when the system theme changes', () => {
      const { listeners } = mockMatchMedia(false)
      localStorage.setItem('heatmap_theme', 'light')
      initTheme()
      listeners[0]({ matches: true })
      expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    })
  })

  describe('toggleTheme', () => {
    it('flips from light to dark and persists the choice', () => {
      mockMatchMedia(false)
      const next = toggleTheme()
      expect(next).toBe('dark')
      expect(localStorage.getItem('heatmap_theme')).toBe('dark')
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })

    it('flips from dark to light', () => {
      mockMatchMedia(true)
      const next = toggleTheme()
      expect(next).toBe('light')
      expect(localStorage.getItem('heatmap_theme')).toBe('light')
    })

    it('still applies the theme even if localStorage.setItem throws', () => {
      mockMatchMedia(false)
      const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('blocked')
      })
      const next = toggleTheme()
      expect(next).toBe('dark')
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
      spy.mockRestore()
    })
  })
})
