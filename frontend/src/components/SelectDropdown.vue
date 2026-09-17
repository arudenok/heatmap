<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import IconBase from './IconBase.vue'

// Одиночный выбор в том же визуальном стиле, что и MultiSelectDropdown (тот же
// .panel, та же выпадающая карточка вместо нативного списка <select>, который
// браузер рисует своим системным видом - светлым, вне темы проекта). В отличие
// от MultiSelectDropdown - только одно значение, и панель закрывается сразу по
// клику на вариант (как обычный <select>, без кнопки "Применить").
const props = defineProps({
  // Каждый элемент - { value, label }. Если полю нужно состояние "не выбрано"
  // (например, "Не указан" у Типа инструмента или "Любой" у одноимённого фильтра) -
  // включите его в options обычным элементом с value: '' , как раньше делали через
  // <option value="">...</option> у нативного select - отдельного пропа для этого нет,
  // значением поля управляет только options+modelValue.
  options: { type: Array, default: () => [] },
  invalid: { type: Boolean, default: false },
  // Компактный вариант - для мелких инлайн-элементов вроде сортировки списка (см.
  // ToolList.vue), где полноразмерное поле формы (width:100%, крупный padding) не подходит.
  compact: { type: Boolean, default: false },
  // Разрешить ввести значение, которого нет в options - тот же смысл, что и у
  // MultiSelectDropdown.allowCustom (например, администратор заводит новый "Тип инструмента").
  // В отличие от MultiSelectDropdown - выбор один, поэтому добавленное значение сразу становится
  // текущим (а не добавляется отдельным "чипом" к списку), и панель закрывается сразу же.
  allowCustom: { type: Boolean, default: false }
})

const selected = defineModel({ default: '' })

const open = ref(false)
const root = ref(null)
const customText = ref('')
const customInput = ref(null)

function addCustom() {
  const value = customText.value.trim()
  if (!value) return
  selected.value = value
  customText.value = ''
  open.value = false
}

const selectedOption = computed(() => props.options.find((opt) => opt.value === selected.value))
// Если текущее значение не найдено среди options (например, ещё не загрузился список) -
// показываем само значение, а не пустую строку, чтобы не терять выбранное на глазах.
const summaryText = computed(() => selectedOption.value?.label ?? selected.value ?? '')
const isPlaceholder = computed(() => !selected.value)

function toggleOpen() {
  open.value = !open.value
}

function choose(opt) {
  selected.value = opt.value
  open.value = false
}

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
  <div ref="root" class="select-dd">
    <button
      type="button"
      class="select-dd-trigger"
      :class="[compact ? 'select-dd-compact' : 'input', { 'select-dd-open': open, 'input-invalid': invalid }]"
      @click="toggleOpen"
    >
      <span class="select-dd-summary" :class="{ 'select-dd-muted': isPlaceholder }">{{ summaryText }}</span>
      <IconBase name="chevronDown" :size="14" class="select-dd-chevron" />
    </button>

    <div v-if="open" class="select-dd-panel panel" :class="{ 'select-dd-panel-compact': compact }">
      <div v-if="allowCustom" class="select-dd-custom">
        <input
          ref="customInput"
          v-model="customText"
          type="text"
          class="select-dd-custom-input"
          placeholder="Свой вариант…"
          @keydown.enter.prevent="addCustom"
          @click.stop
        />
        <button
          type="button"
          class="select-dd-custom-add"
          :disabled="!customText.trim()"
          title="Добавить"
          @click="addCustom"
        >
          <IconBase name="plus" :size="13" />
        </button>
      </div>
      <div class="select-dd-options">
        <button
          v-for="opt in options"
          :key="opt.value"
          type="button"
          class="select-dd-option"
          :class="{ 'select-dd-option-active': opt.value === selected }"
          @click="choose(opt)"
        >
          <span>{{ opt.label }}</span>
          <IconBase v-if="opt.value === selected" name="check" :size="14" class="select-dd-check" />
        </button>
        <p v-if="!options.length" class="select-dd-empty">Нет вариантов</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.select-dd {
  position: relative;
}

.select-dd-trigger {
  /* См. аналогичный комментарий в MultiSelectDropdown.vue - это <button>, а не
     <input>/<div>, поэтому сбрасываем нативный вид кнопки, иначе браузер поверх
     .input дорисовывает свою рамку. */
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

/* Компактный вариант (см. проп compact) - как .sort-select в ToolList.vue раньше:
   без width:100%, меньше паддинги и шрифт, для использования инлайн среди другого
   контента, а не как поле формы во всю ширину колонки. */
.select-dd-compact {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-secondary);
  background-color: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 4px 8px;
  outline: none;
  transition: border-color 0.12s ease;
}
.select-dd-compact:hover {
  border-color: var(--border-strong);
}
.select-dd-open.select-dd-compact,
.select-dd-compact:focus {
  border-color: var(--accent);
}

.select-dd-trigger.input-invalid {
  border-color: var(--danger, #d64545) !important;
  background: var(--danger-soft, rgba(214, 69, 69, 0.06));
}
.select-dd-trigger.input-invalid .select-dd-summary {
  color: var(--danger, #d64545);
}

.select-dd-summary {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-dd-muted {
  color: var(--text-muted);
}

.select-dd-chevron {
  flex-shrink: 0;
  color: var(--text-muted);
  transition: transform 0.12s ease;
}
.select-dd-open .select-dd-chevron {
  transform: rotate(180deg);
}

.select-dd-panel {
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
  overflow-y: auto;
}

/* Компактный триггер узкий по содержимому - без этого панель (left:0;right:0)
   наследовала бы его ширину и обрезала более длинные варианты (например,
   "По дате обновления" в сортировке списка). */
.select-dd-panel-compact {
  left: auto;
  right: 0;
  width: max-content;
  min-width: 100%;
}

.select-dd-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 4px 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.select-dd-custom-input {
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
.select-dd-custom-input:focus {
  border-color: var(--accent);
}

.select-dd-custom-add {
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
.select-dd-custom-add:hover:not(:disabled) {
  background: var(--surface-muted);
}
.select-dd-custom-add:disabled {
  color: var(--text-muted);
  cursor: default;
}

.select-dd-options {
  display: flex;
  flex-direction: column;
}

.select-dd-option {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  width: 100%;
  border: none;
  background: none;
  padding: 8px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font: inherit;
  font-size: 13.5px;
  color: var(--text-primary);
  text-align: left;
}
.select-dd-option:hover {
  background: var(--surface-muted);
}

.select-dd-option-active {
  color: var(--accent-dark, var(--accent));
  font-weight: 600;
}

.select-dd-check {
  flex-shrink: 0;
  color: var(--accent-dark, var(--accent));
}

.select-dd-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 8px;
  margin: 0;
}
</style>
