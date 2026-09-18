import { describe, it, expect } from 'vitest'
import { useConfirm } from '../useConfirm'

describe('useConfirm', () => {
  it('opens the dialog with defaults when called with just a message', async () => {
    const { confirmState, confirm, settle } = useConfirm()
    const promise = confirm('Удалить инструмент?')

    expect(confirmState.open).toBe(true)
    expect(confirmState.message).toBe('Удалить инструмент?')
    expect(confirmState.title).toBe('Подтвердите действие')
    expect(confirmState.confirmLabel).toBe('Подтвердить')
    expect(confirmState.cancelLabel).toBe('Отмена')
    expect(confirmState.danger).toBe(true)
    expect(confirmState.icon).toBe('trash')

    settle(true)
    await expect(promise).resolves.toBe(true)
  })

  it('applies custom options', () => {
    const { confirmState, confirm, settle } = useConfirm()
    confirm('Отправить повторно?', {
      title: 'Повторная отправка',
      confirmLabel: 'Отправить',
      cancelLabel: 'Не сейчас',
      danger: false,
      icon: 'send'
    })

    expect(confirmState.title).toBe('Повторная отправка')
    expect(confirmState.confirmLabel).toBe('Отправить')
    expect(confirmState.cancelLabel).toBe('Не сейчас')
    expect(confirmState.danger).toBe(false)
    expect(confirmState.icon).toBe('send')
    settle(false)
  })

  it('resolves false when the user cancels', async () => {
    const { confirm, settle } = useConfirm()
    const promise = confirm('Удалить?')
    settle(false)
    await expect(promise).resolves.toBe(false)
  })

  it('closes the dialog on settle', () => {
    const { confirmState, confirm, settle } = useConfirm()
    confirm('Удалить?')
    expect(confirmState.open).toBe(true)
    settle(true)
    expect(confirmState.open).toBe(false)
  })

  it('auto-cancels a still-open previous dialog when confirm is called again', async () => {
    const { confirm, settle } = useConfirm()
    const first = confirm('Первый запрос')
    const second = confirm('Второй запрос')

    await expect(first).resolves.toBe(false)

    settle(true)
    await expect(second).resolves.toBe(true)
  })

  it('settle is a no-op when nothing is open', () => {
    const { confirmState, settle } = useConfirm()
    expect(() => settle(true)).not.toThrow()
    expect(confirmState.open).toBe(false)
  })
})
