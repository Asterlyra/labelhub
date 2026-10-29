export const JSON_EDITOR_BASE_VALUE = {
  type: 'jsonEditor',
  field: 'json',
  label: 'JSON编辑器',
} as const

const jsonEditorBaseKeys = new Set(Object.keys(JSON_EDITOR_BASE_VALUE))
const jsonEditorMetadataKeys = new Set([
  'id',
  'sortOrder',
  'content',
  'required',
  'maxLength',
  'placeholder',
  'options',
])

function readAdditionalContent(value: unknown) {
  return typeof value === 'string' ? value.trim().replace(/^,\s*/, '') : ''
}

function parseJsonEditorAdditionalContent(value: unknown) {
  const content = readAdditionalContent(value)

  if (!content) {
    return {}
  }

  const parsedValue = JSON.parse(`{${content}}`) as Record<string, unknown>
  const repeatedBaseKey = Object.keys(parsedValue).find((key) => jsonEditorBaseKeys.has(key))

  if (repeatedBaseKey) {
    throw new Error('type、field、label 为固定字段，无需重复填写')
  }

  return parsedValue
}

export function getJsonEditorValidationError(value: unknown) {
  try {
    parseJsonEditorAdditionalContent(value)
    return ''
  } catch (error) {
    if (error instanceof Error && error.message.includes('固定字段')) {
      return error.message
    }

    return 'JSON 格式不正确，请检查括号、引号和逗号'
  }
}

export function buildJsonEditorTemplateItem(value: unknown) {
  return {
    ...JSON_EDITOR_BASE_VALUE,
    ...parseJsonEditorAdditionalContent(value),
  }
}

export function getJsonEditorAdditionalContent(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return ''
  }

  const source = value as Record<string, unknown>
  let contentSource = source

  if (typeof source.content === 'string') {
    try {
      const parsedContent = JSON.parse(source.content)

      if (parsedContent && typeof parsedContent === 'object' && !Array.isArray(parsedContent)) {
        contentSource = parsedContent as Record<string, unknown>
      }
    } catch {
      return source.content
    }
  }

  const additionalEntries = Object.entries(contentSource).filter(([key]) => {
    return !jsonEditorBaseKeys.has(key) && !jsonEditorMetadataKeys.has(key)
  })

  if (!additionalEntries.length) {
    return ''
  }

  const serializedContent = JSON.stringify(Object.fromEntries(additionalEntries), null, 2)

  return serializedContent.slice(2, -2)
}
