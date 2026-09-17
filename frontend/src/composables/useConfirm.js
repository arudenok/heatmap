import { reactive } from 'vue'

// Единая замена нативному window.confirm() - у него системный вид браузера, выбивающийся
// из тёмной темы приложения (см. ConfirmDialog.vue, который рендерит это состояние).
// Состояние - синглтон на уровне модуля, а не Pinia-стор: одного диалога хватает на всё
// приложение (второй запрос, пока первый не закрыт, просто переиспользует то же окно),
// и не нужно тянуть сюда сложность стора ради одного объекта.
const state = reactive({
  open: false,
  title: '',
  message: '',
  confirmLabel: 'Подтвердить',
  cancelLabel: 'Отмена',
  danger: true,
  icon: null
})

// Резолвер текущего вызова confirm() - вне reactive(state), чтобы Vue не пыталась
// заворачивать функцию в реактивность.
let resolveCurrent = null

/**
 * Показать диалог подтверждения в стиле приложения вместо window.confirm().
 * Возвращает Promise<boolean> - true, если пользователь подтвердил действие.
 */
function confirm(message, options = {}) {
  // Если уже открыт другой диалог - отменяем его (как отмену), чтобы не потерять резолвер.
  if (resolveCurrent) {
    resolveCurrent(false)
    resolveCurrent = null
  }
  state.message = message
  state.title = options.title ?? 'Подтвердите действие'
  state.confirmLabel = options.confirmLabel ?? 'Подтвердить'
  state.cancelLabel = options.cancelLabel ?? 'Отмена'
  // danger=true - кнопка подтверждения красная (удаление и другие необратимые/заметные
  // действия), danger=false - обычная акцентная (например, просто отправить повторно).
  state.danger = options.danger ?? true
  state.icon = options.icon ?? 'trash'
  state.open = true
  return new Promise((resolve) => {
    resolveCurrent = resolve
  })
}

function settle(value) {
  state.open = false
  resolveCurrent?.(value)
  resolveCurrent = null
}

export function useConfirm() {
  return { confirmState: state, confirm, settle }
}
