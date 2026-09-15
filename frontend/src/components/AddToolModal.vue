<script setup>
import { reactive, ref, watch, computed } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'
import MultiSelectDropdown from './MultiSelectDropdown.vue'
import ComboboxInput from './ComboboxInput.vue'

const auth = useAuthStore()

const props = defineProps({
  filterOptions: { type: Object, default: () => ({ roles: [], frameworks: [], segments: [] }) },
  // Если передан - модалка работает в режиме редактирования уже существующей заявки
  // (PATCH вместо POST), а не создания новой.
  editTool: { type: Object, default: null }
})

const open = defineModel({ default: false })
const emit = defineEmits(['created', 'updated'])

const isEditMode = computed(() => !!props.editTool)
// Своя заявка на модерации редактируется иначе, чем уже опубликованный/отклонённый инструмент -
// правки последнего от имени автора (не администратора) отправляют его на повторную модерацию,
// поэтому тексты подписей отличаются.
const isPendingEdit = computed(() => isEditMode.value && props.editTool?.status === 'PENDING')
const isResubmitEdit = computed(
  () =>
    isEditMode.value &&
    !auth.isAdmin &&
    (props.editTool?.status === 'PUBLISHED' || props.editTool?.status === 'REJECTED')
)

const form = reactive({
  name: '',
  description: '',
  roles: [],
  framework: '',
  segments: [],
  sourceLabel: ''
})

const loading = ref(false)
const error = ref('')

// При открытии модалки в режиме редактирования - подставляем текущие данные заявки в форму.
watch(
  () => [open.value, props.editTool],
  ([isOpen, tool]) => {
    if (!isOpen) return
    if (tool) {
      form.name = tool.name
      form.description = tool.description
      form.roles = [...tool.roles]
      form.framework = tool.framework
      form.segments = [...tool.segments]
      form.sourceLabel = tool.sourceLabel
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

// Незаполненные/некорректные обязательные поля подсвечиваются красным при попытке отправки.
const invalidFields = reactive({
  name: false,
  description: false,
  roles: false,
  framework: false,
  segments: false,
  sourceLabel: false
})

const URL_PATTERN = /^https?:\/\/.+/i

function clearFieldError(field) {
  invalidFields[field] = false
}

function close() {
  open.value = false
  error.value = ''
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.roles = []
  form.framework = ''
  form.segments = []
  form.sourceLabel = ''
  Object.keys(invalidFields).forEach((key) => (invalidFields[key] = false))
}

function validate() {
  const sourceTrimmed = form.sourceLabel.trim()
  const sourceEmpty = !sourceTrimmed
  const sourceBadFormat = !sourceEmpty && !URL_PATTERN.test(sourceTrimmed)

  invalidFields.name = !form.name.trim()
  invalidFields.description = !form.description.trim()
  invalidFields.roles = !form.roles.length
  invalidFields.framework = !form.framework.trim()
  invalidFields.segments = !form.segments.length
  invalidFields.sourceLabel = sourceEmpty || sourceBadFormat

  const hasEmptyRequired =
    invalidFields.name ||
    invalidFields.description ||
    invalidFields.roles ||
    invalidFields.framework ||
    invalidFields.segments ||
    sourceEmpty

  if (hasEmptyRequired) {
    error.value = 'Заполните все обязательные поля, отмеченные *'
    return false
  }
  if (sourceBadFormat) {
    error.value = 'Ссылка на инструмент должна начинаться с http:// или https://'
    return false
  }
  return true
}

async function onSubmit() {
  error.value = ''
  if (!validate()) return

  loading.value = true
  try {
    if (isEditMode.value) {
      const { data } = await api.patch(`/tools/${props.editTool.id}`, { ...form })
      emit('updated', data)
    } else {
      const { data } = await api.post('/tools', { ...form })
      emit('created', data)
    }
    resetForm()
    open.value = false
  } catch (e) {
    error.value = extractErrorMessage(e, isEditMode.value ? 'Не удалось сохранить изменения' : 'Не удалось отправить заявку')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3>
          <IconBase :name="isEditMode ? 'edit' : 'plus'" :size="17" />
          {{
            isEditMode
              ? isPendingEdit
                ? 'Редактировать заявку'
                : isResubmitEdit
                ? 'Редактировать и отправить на модерацию'
                : 'Редактировать инструмент'
              : 'Добавить инструмент'
          }}
        </h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <p v-if="isPendingEdit" class="modal-hint">Изменения сохранятся в заявке, которая всё ещё находится на модерации.</p>
      <p v-else-if="isResubmitEdit" class="modal-hint">Изменения отправят инструмент на повторную модерацию.</p>
      <p v-else-if="isEditMode" class="modal-hint">Изменения будут сохранены сразу, без повторной модерации.</p>
      <p v-else class="modal-hint">Заявка попадёт на этап <strong>Access</strong> и будет опубликована после проверки администратором.</p>

      <form class="modal-form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="tool-name">Название <span class="required">*</span></label>
          <input
            id="tool-name"
            v-model="form.name"
            class="input"
            :class="{ 'input-invalid': invalidFields.name }"
            type="text"
            maxlength="255"
            :placeholder="invalidFields.name ? 'Обязательное поле' : ''"
            @input="clearFieldError('name')"
          />
        </div>

        <div class="field">
          <label for="tool-desc">Описание <span class="required">*</span></label>
          <textarea
            id="tool-desc"
            v-model="form.description"
            class="input"
            :class="{ 'input-invalid': invalidFields.description }"
            rows="3"
            :placeholder="invalidFields.description ? 'Обязательное поле' : ''"
            @input="clearFieldError('description')"
          ></textarea>
        </div>

        <div class="form-row">
          <div class="field">
            <label for="tool-role">Роль / направление <span class="required">*</span></label>
            <MultiSelectDropdown
              id="tool-role"
              v-model="form.roles"
              :options="filterOptions.roles"
              all-label="Выберите роли"
              :invalid="invalidFields.roles"
              @update:model-value="clearFieldError('roles')"
            />
          </div>
          <div class="field">
            <label for="tool-framework">Агентский фреймворк <span class="required">*</span></label>
            <ComboboxInput
              id="tool-framework"
              v-model="form.framework"
              :options="filterOptions.frameworks"
              :placeholder="invalidFields.framework ? 'Обязательное поле' : 'Выберите или введите своё'"
              :invalid="invalidFields.framework"
              @update:model-value="clearFieldError('framework')"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label for="tool-segment">Сегмент <span class="required">*</span></label>
            <MultiSelectDropdown
              id="tool-segment"
              v-model="form.segments"
              :options="filterOptions.segments"
              all-label="Выберите сегменты"
              :invalid="invalidFields.segments"
              @update:model-value="clearFieldError('segments')"
            />
          </div>
          <div class="field">
            <label for="tool-source">Ссылка на инструмент <span class="required">*</span></label>
            <input
              id="tool-source"
              v-model="form.sourceLabel"
              class="input"
              :class="{ 'input-invalid': invalidFields.sourceLabel }"
              type="text"
              :placeholder="invalidFields.sourceLabel ? 'Обязательное поле' : 'https://…'"
              @input="clearFieldError('sourceLabel')"
            />
          </div>
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>

        <div class="modal-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">
            {{
              isEditMode
                ? isResubmitEdit
                  ? (loading ? 'Отправляем…' : 'Сохранить и отправить на модерацию')
                  : (loading ? 'Сохраняем…' : 'Сохранить изменения')
                : (loading ? 'Отправляем…' : 'Отправить на модерацию')
            }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(20, 24, 38, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 50;
}

.modal {
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px 26px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  margin: 0;
}

.icon-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
}
.icon-btn:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}

.modal-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 18px;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.input-invalid {
  border-color: var(--danger, #d64545) !important;
  background: var(--danger-soft, rgba(214, 69, 69, 0.06));
}
.input-invalid:focus {
  outline-color: var(--danger, #d64545);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

@media (max-width: 520px) {
  .form-row { grid-template-columns: 1fr; }
}
</style>
