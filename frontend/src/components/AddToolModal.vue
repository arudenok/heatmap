<script setup>
import { reactive, ref, watch, computed } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'
import MultiSelectDropdown from './MultiSelectDropdown.vue'
import SelectDropdown from './SelectDropdown.vue'
import ToolDetailModal from './ToolDetailModal.vue'

const auth = useAuthStore()

const props = defineProps({
  filterOptions: { type: Object, default: () => ({ roles: [], frameworks: [], constraints: [], toolTypes: [] }) },
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
// DRAFT - тот же случай, что PUBLISHED/REJECTED: автор правит инструмент, отозванный с
// модерации (см. ToolService.update/shouldResubmitOnEdit), и правка отправляет его повторно.
const isResubmitEdit = computed(
  () =>
    isEditMode.value &&
    !auth.isAdmin &&
    ['PUBLISHED', 'REJECTED', 'DRAFT'].includes(props.editTool?.status)
)

// Этап зрелости и статус в форме создания доступны только администратору (см. admin-params-box
// в шаблоне) - обычный пользователь всегда попадает на Access/PENDING (см. ToolService.create).
// Статус при создании намеренно ограничен "На модерации"/"Опубликован" - остальные значения
// (REJECTED/ARCHIVED) не имеют смысла для только что создаваемого инструмента. При редактировании
// же администратору доступны все статусы и метрики влияния (см. admin-params-box и ToolService.update -
// там ограничения на статус нет).
const STAGE_OPTIONS = [
  { value: 'ACCESS', label: 'Access' },
  { value: 'USAGE', label: 'Usage' },
  { value: 'HABIT', label: 'Habit' },
  { value: 'STANDARD', label: 'Process Standard' }
]
const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'На модерации' },
  { value: 'PUBLISHED', label: 'Опубликован' }
]
const STATUS_OPTIONS_EDIT = [
  ...STATUS_OPTIONS,
  { value: 'REJECTED', label: 'Отклонён' },
  { value: 'ARCHIVED', label: 'Архивирован' },
  { value: 'DRAFT', label: 'Черновик' }
]
const statusOptions = computed(() => (isEditMode.value ? STATUS_OPTIONS_EDIT : STATUS_OPTIONS))

// Тип инструмента - необязательная категория. Базовый набор приходит с бэкенда
// (filterOptions.toolTypes - см. ToolService.filterOptions/TOOL_TYPE_OPTIONS в ToolDtos.kt)
// плюс любые уже сохранённые "свои варианты" администратора (см. allow-custom у SelectDropdown
// ниже - тот же принцип, что и allow-custom у MultiSelectDropdown для роли/ограничений).
// "Не указан" в список не входит - это состояние "ничего не выбрано" у самого SelectDropdown,
// добавляем его явным элементом с value: '', как раньше делали через <option value="">.
const TOOL_TYPE_SELECT_OPTIONS = computed(() => [
  { value: '', label: 'Не указан' },
  ...props.filterOptions.toolTypes.map((opt) => ({ value: opt, label: opt }))
])

const form = reactive({
  name: '',
  shortDescription: '',
  description: '',
  roles: [],
  framework: [],
  toolType: '',
  constraints: [],
  sourceLabel: '',
  stage: 'ACCESS',
  status: 'PENDING',
  // Метрики влияния - редактируются администратором только при редактировании уже существующего
  // инструмента (см. admin-params-box), у только что создаваемого они всегда нулевые/не заданы.
  downloads: 0,
  dau: '',
  efficiencyPct: 0,
  // Новое поле "Сегмент" - видно и редактируется только администратором, обычно на модерации
  // (см. admin-params-box) - как и метрики выше, доступно только при редактировании.
  segment: ''
})

const loading = ref(false)
const error = ref('')

// Заполняется, когда сервер отклоняет сохранение из-за дубликата "Ссылки на инструмент"
// (см. ToolService.create/update - DuplicateSourceLabelException, только среди активных
// карточек). Вместо обычного текста ошибки показываем кликабельное имя существующего
// инструмента - клик открывает его карточку в отдельной ToolDetailModal прямо поверх этой формы.
const duplicateTool = ref(null)
const duplicateDetailOpen = ref(false)
const duplicateDetailTool = ref(null)

async function openDuplicateTool() {
  if (!duplicateTool.value) return
  try {
    const { data } = await api.get(`/tools/${duplicateTool.value.id}`)
    duplicateDetailTool.value = data
    duplicateDetailOpen.value = true
  } catch {
    // если карточка вдруг недоступна (например, удалена) - молча ничего не открываем,
    // само предупреждение с именем всё равно остаётся видно
  }
}

// Незаполненные/некорректные обязательные поля подсвечиваются красным при попытке отправки.
// Краткое описание и ограничения необязательны, поэтому в этот список не входят.
// Объявлено до watch(..., { immediate: true }) ниже - тот при монтировании с открытой
// модалкой (open=true) сразу вызывает resetForm(), а она обращается к invalidFields;
// будь объявление после watch, это упало бы с ReferenceError (temporal dead zone у const).
const invalidFields = reactive({
  name: false,
  description: false,
  roles: false,
  sourceLabel: false
})

// При открытии модалки в режиме редактирования - подставляем текущие данные заявки в форму.
watch(
  () => [open.value, props.editTool],
  ([isOpen, tool]) => {
    if (!isOpen) return
    if (tool) {
      form.name = tool.name
      form.shortDescription = tool.shortDescription || ''
      form.description = tool.description
      form.roles = [...tool.roles]
      form.framework = [...(tool.framework || [])]
      form.toolType = tool.toolType || ''
      form.constraints = [...(tool.constraints || [])]
      form.sourceLabel = tool.sourceLabel || ''
      // Этап/статус/метрики - только для отображения и редактирования администратором
      // (см. admin-params-box), но подставляем их независимо от роли - не отправятся,
      // если auth.isAdmin === false (см. onSubmit).
      form.stage = tool.stage
      form.status = tool.status
      form.downloads = tool.downloads
      form.dau = tool.dau ?? ''
      form.efficiencyPct = tool.efficiencyPct
      form.segment = tool.segment || ''
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

// Ссылка на инструмент принимается только с внутренних корпоративных сервисов (см. тот же
// список и комментарий на бэкенде - ToolDtos.kt/URL_PATTERN). Токен должен начинать доменную
// метку (после точки или сразу после схемы), а не просто где-то встречаться в строке.
const ALLOWED_SOURCE_HOSTS = ['sc-ci', 'sbrf-bitbucket', 'stash', 'confluence', 'jira', 'mapp', 'sbertrack', 'onework']
const URL_PATTERN = new RegExp(
  `^https?://(?:[\\w-]+\\.)*(${ALLOWED_SOURCE_HOSTS.join('|')})[\\w.-]*(?::\\d+)?(/.*)?$`,
  'i'
)

function clearFieldError(field) {
  invalidFields[field] = false
  // Как только ссылку снова редактируют - прошлое предупреждение о дубликате уже не
  // актуально (пользователь мог изменить её на другую).
  if (field === 'sourceLabel') {
    duplicateTool.value = null
  }
}

function close() {
  open.value = false
  error.value = ''
  duplicateTool.value = null
}

function resetForm() {
  form.name = ''
  form.shortDescription = ''
  form.description = ''
  form.roles = []
  form.framework = []
  form.toolType = ''
  form.constraints = []
  form.sourceLabel = ''
  form.stage = 'ACCESS'
  form.status = 'PENDING'
  form.downloads = 0
  form.dau = ''
  form.efficiencyPct = 0
  form.segment = ''
  Object.keys(invalidFields).forEach((key) => (invalidFields[key] = false))
  duplicateTool.value = null
}

function validate() {
  const sourceTrimmed = form.sourceLabel.trim()
  const sourceEmpty = !sourceTrimmed
  // Ссылка обязательна для обычного пользователя, но необязательна для администратора -
  // он может завести карточку до появления публичной ссылки (см. ToolService.create/update
  // и кнопку "Скачать" в ToolDetailModal, которая тогда подсказывает, что ссылки нет).
  const sourceRequired = !auth.isAdmin
  const sourceBadFormat = !sourceEmpty && !URL_PATTERN.test(sourceTrimmed)

  invalidFields.name = !form.name.trim()
  invalidFields.description = !form.description.trim()
  invalidFields.roles = !form.roles.length
  invalidFields.sourceLabel = (sourceRequired && sourceEmpty) || sourceBadFormat

  const hasEmptyRequired =
    invalidFields.name ||
    invalidFields.description ||
    invalidFields.roles ||
    (sourceRequired && sourceEmpty)

  if (hasEmptyRequired) {
    error.value = 'Заполните все обязательные поля, отмеченные *'
    return false
  }
  if (sourceBadFormat) {
    error.value = `Ссылка должна вести на корпоративный сервис (${ALLOWED_SOURCE_HOSTS.join(', ')})`
    return false
  }
  return true
}

async function onSubmit() {
  error.value = ''
  duplicateTool.value = null
  if (!validate()) return

  loading.value = true
  try {
    const payload = {
      name: form.name,
      shortDescription: form.shortDescription.trim() || null,
      description: form.description,
      roles: form.roles,
      framework: form.framework,
      toolType: form.toolType || null,
      constraints: form.constraints,
      sourceLabel: form.sourceLabel.trim() || null
    }
    // "Параметры администратора" (этап/статус) отправляются и при создании, и при редактировании -
    // при редактировании администратору дополнительно доступны метрики влияния (см. admin-params-box).
    // CreateToolRequest не содержит полей downloads/dau/efficiencyPct - их отправляем только
    // при редактировании, чтобы не посылать лишние поля при создании.
    if (auth.isAdmin) {
      payload.stage = form.stage
      payload.status = form.status
      if (isEditMode.value) {
        payload.downloads = Number(form.downloads) || 0
        payload.dau = form.dau === '' || form.dau === null ? null : Number(form.dau)
        payload.efficiencyPct = Number(form.efficiencyPct) || 0
        payload.segment = form.segment.trim() || null
      }
    }

    if (isEditMode.value) {
      const { data } = await api.patch(`/tools/${props.editTool.id}`, payload)
      emit('updated', data)
    } else {
      const { data } = await api.post('/tools', payload)
      emit('created', data)
    }
    resetForm()
    open.value = false
  } catch (e) {
    // Дубликат "Ссылки на инструмент" (см. ToolService.create/update) - сервер отвечает 409
    // с id и именем существующего инструмента (см. GlobalExceptionHandler/ErrorResponse) -
    // вместо обычного текста ошибки показываем кликабельное имя (см. duplicateTool в шаблоне).
    const conflictId = e?.response?.status === 409 ? e.response.data?.conflictToolId : null
    if (conflictId) {
      duplicateTool.value = { id: conflictId, name: e.response.data?.conflictToolName || '' }
      invalidFields.sourceLabel = true
    } else {
      error.value = extractErrorMessage(e, isEditMode.value ? 'Не удалось сохранить изменения' : 'Не удалось отправить заявку')
    }
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
      <p v-else-if="auth.isAdmin" class="modal-hint">Как администратор вы можете сразу указать этап и статус в блоке ниже - иначе заявка попадёт на этап <strong>Access</strong> и будет ждать модерации.</p>
      <p v-else class="modal-hint">Заявка попадёт на этап <strong>Access</strong> и будет опубликована после проверки администратором.</p>
      <p v-if="isEditMode && auth.isAdmin" class="modal-hint">Полное редактирование инструмента - включая этап, статус и метрики влияния (блок ниже).</p>

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
          <label for="tool-short-desc">Краткое описание</label>
          <input
            id="tool-short-desc"
            v-model="form.shortDescription"
            class="input"
            type="text"
            maxlength="300"
            placeholder="1-2 предложения для карточки инструмента (необязательно)"
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

        <div class="field">
          <label for="tool-type">Тип инструмента</label>
          <SelectDropdown
            id="tool-type"
            v-model="form.toolType"
            :options="TOOL_TYPE_SELECT_OPTIONS"
            :allow-custom="auth.isAdmin"
          />
        </div>

        <div class="field">
          <label for="tool-role">Роль / направление <span class="required">*</span></label>
          <MultiSelectDropdown
            id="tool-role"
            v-model="form.roles"
            :options="filterOptions.roles"
            all-label="Выберите роли"
            :invalid="invalidFields.roles"
            :allow-custom="auth.isAdmin"
            @update:model-value="clearFieldError('roles')"
          />
        </div>
        <div class="field">
          <label for="tool-framework">Агентский фреймворк</label>
          <MultiSelectDropdown
            id="tool-framework"
            v-model="form.framework"
            :options="filterOptions.frameworks"
            all-label="Выберите фреймворки (необязательно)"
            :allow-custom="auth.isAdmin"
          />
        </div>

        <div class="field">
          <label for="tool-constraints">Ограничения</label>
          <MultiSelectDropdown
            id="tool-constraints"
            v-model="form.constraints"
            :options="filterOptions.constraints"
            all-label="Выберите ограничения (необязательно)"
            :allow-custom="auth.isAdmin"
          />
        </div>
        <div class="field">
          <label for="tool-source">
            Ссылка на инструмент <span v-if="!auth.isAdmin" class="required">*</span>
          </label>
          <input
            id="tool-source"
            v-model="form.sourceLabel"
            class="input"
            :class="{ 'input-invalid': invalidFields.sourceLabel }"
            type="text"
            :placeholder="
              invalidFields.sourceLabel && !auth.isAdmin
                ? 'Обязательное поле'
                : auth.isAdmin
                ? 'https://… (необязательно для администратора)'
                : 'https://…'
            "
            @input="clearFieldError('sourceLabel')"
          />
          <p class="field-hint">Только корпоративные сервисы: sc-ci, sbrf-bitbucket, stash, confluence, jira, mapp, sbertrack, onework</p>
        </div>

        <!-- Этап и статус доступны администратору и при создании, и при редактировании (см.
             ToolService.create/update - у обычного пользователя они не показываются: при создании
             всегда Access/PENDING, при редактировании статус меняется только через модерацию/архивацию).
             Метрики влияния (скачивания/DAU/эффективность) редактируются только у уже существующего
             инструмента - у только что создаваемого их взять неоткуда. -->
        <div v-if="auth.isAdmin" class="admin-params-box">
          <div class="admin-params-title"><IconBase name="shield" :size="13" /> Параметры администратора</div>
          <div class="field">
            <label for="tool-stage">Этап зрелости</label>
            <SelectDropdown id="tool-stage" v-model="form.stage" :options="STAGE_OPTIONS" />
          </div>
          <div class="field">
            <label for="tool-status">Статус</label>
            <SelectDropdown id="tool-status" v-model="form.status" :options="statusOptions" />
          </div>
          <div v-if="isEditMode" class="field">
            <label for="tool-downloads">Скачиваний</label>
            <input id="tool-downloads" v-model.number="form.downloads" class="input" type="number" min="0" />
          </div>
          <div v-if="isEditMode" class="field">
            <label for="tool-dau">DAU</label>
            <input id="tool-dau" v-model="form.dau" class="input" type="number" min="0" placeholder="не задано" />
          </div>
          <div v-if="isEditMode" class="field">
            <label for="tool-efficiency">Эффективность, %</label>
            <input id="tool-efficiency" v-model.number="form.efficiencyPct" class="input" type="number" min="0" max="100" />
          </div>
          <!-- Отдельное поле "Сегмент" - не путать с "Ограничениями" выше (это разные поля,
               см. комментарий в AiTool.kt/segment). Видно и заполняется только администратором,
               обычно при рассмотрении заявки на модерации. -->
          <div v-if="isEditMode" class="field">
            <label for="tool-segment">Сегмент</label>
            <input
              id="tool-segment"
              v-model="form.segment"
              class="input"
              type="text"
              maxlength="128"
              placeholder="Внутренняя пометка администратора (необязательно)"
            />
          </div>
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="duplicateTool" class="error-text">
          Такая ссылка уже используется инструментом
          <button type="button" class="link-btn" @click="openDuplicateTool">{{ duplicateTool.name }}</button>
        </p>

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

  <!-- Карточка инструмента-владельца дублирующейся ссылки (см. duplicateTool/openDuplicateTool
       выше) - отдельная модалка поверх этой формы, вне .modal-backdrop, иначе клики внутри неё
       всплывали бы до @click.self этой формы и закрывали её вместе с карточкой. -->
  <ToolDetailModal v-model="duplicateDetailOpen" :tool="duplicateDetailTool" />
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

.field-hint {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--text-muted);
}

.link-btn {
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}
.link-btn:hover {
  color: var(--accent-dark);
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

/* "Параметры администратора" - выделяем зелёной рамкой/подложкой, чтобы обычные поля формы
   визуально не путались с админскими (видны только администратору и только при создании). */
.admin-params-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--accent);
  border-radius: var(--radius-md);
  background: var(--accent-soft);
}

.admin-params-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--accent-dark);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

</style>
