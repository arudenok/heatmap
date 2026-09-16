<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import IconBase from './IconBase.vue'

const props = defineProps({
  options: { type: Array, default: () => [] },
  // Текст в закрытом поле, когда ничего не выбрано.
  allLabel: { type: String, default: 'Все' },
  invalid: { type: Boolean, default: false },
  // Разрешить ввести значение, которого нет в options (например, чтобы администратор мог
  // завести новую роль - она станет доступна всем в фильтрах, как только появится хотя бы
  // один инструмент с этой ролью, см. ToolService.filterOptions - список ролей вычисляется
  // из уже сохранённых инструментов, отдельного справочника ролей в бэкенде нет).
  allowCustom: { type: Boolean, default: false }
})

// Массив выбранных значений.
const selected = defineModel({ default: () => [] })

const open = ref(false)
const root = ref(null)
const customText = ref('')
const customInput = ref(null)

// Значения, которые выбраны, но отсутствуют в общем списке options - то есть только что
// введены вручную (см. addCustom). Показываем их отдельными "чипами" сверху панели.
const customSelected = computed(() => selected.value.filter((v) => !props.options.includes(v)))

function addCustom() {
  const value = customText.value.trim()
  if (!value) return
  if (!selected.value.includes(value)) {
    selected.value = [...selected.value, value]
  }
  customText.value = ''
  customInput.value?.focus()
}

function removeCustom(value) {
  selected.value = selected.value.filter((v) => v !== value)
}

function toggleOpen() {
  open.value = !open.value
}

function isChecked(opt) {
  return selected.value.includes(opt)
}

function toggleOption(opt) {
  if (isChecked(opt)) {
    selected.value = selected.value.filter((v) => v !== opt)
  } else {
    selected.value = [...selected.value, opt]
  }
}

function clearAll(event) {
  event.stopPropagation()
  selected.value = []
}

const summaryText = computed(() => {
  if (!selected.value.length) return props.invalid ? 'Обязательное поле' : props.allLabel
  if (selected.value.length === 1) return selected.value[0]
  return `Выбрано: ${selected.value.length}`
})

function onDocClick(event) {
  if (root.value && !root.value.contains(event.target)) {
    open.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="multiselect">
    <button
      type="button"
      class="multiselect-trigger input"
      :class="{ 'multiselect-open': open, 'input-invalid': invalid }"
      @click="toggleOpen"
    >
      <span class="multiselect-summary" :class="{ 'multiselect-muted': !selected.length }">{{ summaryText }}</span>
      <span
        v-if="selected.length"
        class="multiselect-clear"
        role="button"
        tabindex="0"
        title="Сбросить"
        @click="clearAll"
        @keydown.enter="clearAll"
      >
        <IconBase name="x" :size="12" />
      </span>
      <IconBase name="chevronDown" :size="14" class="multiselect-chevron" />
    </button>

    <div v-if="open" class="multiselect-panel panel">
      <div v-if="allowCustom" class="multiselect-custom">
        <input
          ref="customInput"
          v-model="customText"
          type="text"
          class="multiselect-custom-input"
          placeholder="Свой вариант…"
          @keydown.enter.prevent="addCustom"
          @click.stop
        />
        <button
          type="button"
          class="multiselect-custom-add"
          :disabled="!customText.trim()"
          title="Добавить"
          @click="addCustom"
        >
          <IconBase name="plus" :size="13" />
        </button>
      </div>
      <div v-if="allowCustom && customSelected.length" class="multiselect-chips">
        <span v-for="v in customSelected" :key="v" class="multiselect-chip">
          {{ v }}
          <span role="button" tabindex="0" title="Убрать" @click="removeCustom(v)" @keydown.enter="removeCustom(v)">
            <IconBase name="x" :size="10" />
          </span>
        </span>
      </div>
      <div class="multiselect-options">
        <label v-if="options.length" v-for="opt in options" :key="opt" class="multiselect-option">
          <input type="checkbox" :checked="isChecked(opt)" @change="toggleOption(opt)" />
          <span>{{ opt }}</span>
        </label>
        <p v-else class="multiselect-empty">Нет вариантов</p>
      </div>
      <!-- Выбор применяется сразу по клику на чекбокс, но без явной кнопки было
           неочевидно, как "закрыть" список - приходилось случайно кликать мимо.
           Кнопка ничего дополнительно не сохраняет, просто закрывает панель. -->
      <button type="button" class="multiselect-apply" @click="open = false">Применить</button>
    </div>
  </div>
</template>

<style scoped>
.multiselect {
  position: relative;
}

.multiselect-trigger {
  /* Это <button>, а не <input>/<div> - без сброса нативного вида браузер (особенно
     Safari) поверх заданных border/border-radius из .input подрисовывает свою рамку
     кнопки, из-за чего углы/границы выглядели "кривыми" на фоне соседнего
     ComboboxInput (тот - обычный div, этой проблемы не было). */
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  text-align: left;
}

/* .input-invalid обычно определяется в scoped-стилях компонента-родителя
   (например AddToolModal), но scoped-стили не пробивают границу компонента -
   поэтому дублируем то же самое здесь для собственной кнопки-триггера. */
.multiselect-trigger.input-invalid {
  border-color: var(--danger, #d64545) !important;
  background: var(--danger-soft, rgba(214, 69, 69, 0.06));
}
.multiselect-trigger.input-invalid .multiselect-summary {
  color: var(--danger, #d64545);
}

.multiselect-summary {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.multiselect-muted {
  color: var(--text-muted);
}

.multiselect-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  flex-shrink: 0;
  border-radius: 999px;
  padding: 2px;
}
.multiselect-clear:hover {
  color: var(--text-primary);
  background: var(--surface-muted);
}

.multiselect-chevron {
  flex-shrink: 0;
  color: var(--text-muted);
  transition: transform 0.12s ease;
}
.multiselect-open .multiselect-chevron {
  transform: rotate(180deg);
}

.multiselect-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 40;
  max-height: 260px;
  display: flex;
  flex-direction: column;
  padding: 6px;
  box-shadow: 0 8px 24px rgba(20, 24, 38, 0.14);
}

.multiselect-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 4px 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.multiselect-custom-input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-primary);
  font: inherit;
  font-size: 13.5px;
  padding: 6px 8px;
  outline: none;
}
.multiselect-custom-input:focus {
  border-color: var(--accent);
}

.multiselect-custom-add {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--accent-dark, var(--accent));
  cursor: pointer;
}
.multiselect-custom-add:hover:not(:disabled) {
  background: var(--surface-muted);
}
.multiselect-custom-add:disabled {
  color: var(--text-muted);
  cursor: default;
}

.multiselect-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 4px 8px;
}

.multiselect-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 6px 3px 9px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-dark);
  font-size: 12.5px;
  font-weight: 600;
}

.multiselect-chip span[role='button'] {
  display: inline-flex;
  cursor: pointer;
  opacity: 0.75;
}
.multiselect-chip span[role='button']:hover {
  opacity: 1;
}

.multiselect-options {
  overflow-y: auto;
}

.multiselect-apply {
  flex-shrink: 0;
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
  border-left: none;
  border-right: none;
  border-bottom: none;
  background: none;
  color: var(--accent-dark, var(--accent));
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  padding-bottom: 4px;
}
.multiselect-apply:hover {
  background: var(--surface-muted);
}

.multiselect-option {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13.5px;
}
.multiselect-option:hover {
  background: var(--surface-muted);
}

.multiselect-option input {
  accent-color: var(--accent);
  cursor: pointer;
}

.multiselect-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 8px;
  margin: 0;
}
</style>
