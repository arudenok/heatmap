<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import IconBase from './IconBase.vue'

// Текстовое поле с подсказками из фиксированного списка, но без запрета на
// свободный ввод. Раньше для этого использовался нативный <input list> +
// <datalist> - у него в разных браузерах/темах рисуется собственный, никак
// не стилизуемый через CSS попап подсказок (в Chrome - чёрный треугольник +
// системный список), что визуально "криво" смотрелось на фоне остальных
// кастомных выпадающих списков в форме. Этот компонент рисует точно такую
// же панель, как MultiSelectDropdown, но для одиночного текстового значения.
const props = defineProps({
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  invalid: { type: Boolean, default: false }
})

const value = defineModel({ default: '' })

const open = ref(false)
const root = ref(null)
const inputEl = ref(null)

const filteredOptions = computed(() => {
  const q = value.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter((opt) => opt.toLowerCase().includes(q))
})

function openPanel() {
  open.value = true
}

function toggleChevron() {
  if (open.value) {
    open.value = false
  } else {
    open.value = true
    inputEl.value?.focus()
  }
}

function pick(opt) {
  value.value = opt
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
  <div ref="root" class="combobox">
    <div class="combobox-field input" :class="{ 'combobox-open': open, 'input-invalid': invalid }">
      <input
        ref="inputEl"
        v-model="value"
        type="text"
        class="combobox-native-input"
        :placeholder="placeholder"
        autocomplete="off"
        @focus="openPanel"
      />
      <span class="combobox-chevron-btn" role="button" tabindex="-1" @mousedown.prevent="toggleChevron">
        <IconBase name="chevronDown" :size="14" class="combobox-chevron" />
      </span>
    </div>

    <div v-if="open" class="combobox-panel panel">
      <div class="combobox-options">
        <div
          v-for="opt in filteredOptions"
          :key="opt"
          class="combobox-option"
          @mousedown.prevent="pick(opt)"
        >
          {{ opt }}
        </div>
        <p v-if="!filteredOptions.length" class="combobox-empty">Нет совпадений - можно ввести своё значение</p>
      </div>
      <!-- Поле не ограничено списком подсказок - явно напоминаем об этом,
           иначе выглядит как обычный select, где можно выбрать только из списка. -->
      <p class="combobox-hint">Можно выбрать из списка или ввести свой вариант</p>
    </div>
  </div>
</template>

<style scoped>
.combobox {
  position: relative;
}

.combobox-field {
  display: flex;
  align-items: center;
  padding: 0;
}

/* .input:focus в глобальных стилях сработал бы только если фокус получит
   сам div-обёртка, а реально фокусируется вложенный <input> - поэтому
   подсветку рамки при фокусе дублируем через :focus-within. */
.combobox-field:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

/* .input-invalid обычно определяется в scoped-стилях компонента-родителя,
   но scoped-стили не пробивают границу компонента - дублируем здесь. */
.combobox-field.input-invalid {
  border-color: var(--danger, #d64545) !important;
  background: var(--danger-soft, rgba(214, 69, 69, 0.06));
}
.combobox-field.input-invalid .combobox-native-input::placeholder {
  color: var(--danger, #d64545);
  opacity: 1;
}

.combobox-native-input {
  flex: 1;
  border: none;
  outline: none;
  background: none;
  color: inherit;
  font: inherit;
  padding: 9px 0 9px 12px;
  min-width: 0;
}

.combobox-chevron-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 10px;
  cursor: pointer;
  flex-shrink: 0;
}

.combobox-chevron {
  color: var(--text-muted);
  transition: transform 0.12s ease;
}
.combobox-open .combobox-chevron {
  transform: rotate(180deg);
}

.combobox-panel {
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

.combobox-options {
  overflow-y: auto;
}

.combobox-option {
  padding: 7px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13.5px;
}
.combobox-option:hover {
  background: var(--surface-muted);
}

.combobox-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 8px;
  margin: 0;
}

.combobox-hint {
  flex-shrink: 0;
  margin: 0;
  padding: 7px 8px 4px;
  border-top: 1px solid var(--border);
  font-size: 11.5px;
  color: var(--text-muted);
}
</style>
