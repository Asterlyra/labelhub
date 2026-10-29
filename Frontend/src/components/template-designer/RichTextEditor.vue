<script setup lang="ts">
import { onMounted, shallowRef, watch } from 'vue'
import type { CanvasComponent, CanvasComponentConfig } from '../../types/template'

type RichTextCommand =
  | 'undo'
  | 'redo'
  | 'removeFormat'
  | 'copyFormat'
  | 'bold'
  | 'italic'
  | 'underline'
  | 'strikeThrough'
  | 'justifyLeft'
  | 'insertUnorderedList'
  | 'insertOrderedList'
  | 'indent'
  | 'insertImage'
  | 'insertTable'
  | 'createLink'
  | 'inlineCode'
  | 'signature'
  | 'fullscreen'

interface RichTextTool {
  label: string
  text: string
  command: RichTextCommand
}

interface RichTextFormat {
  fontName: string
  fontSize: string
  foreColor: string
  backColor: string
  bold: boolean
  italic: boolean
  underline: boolean
  strikeThrough: boolean
}

const props = defineProps<{
  item: CanvasComponent
}>()

const emit = defineEmits<{
  updateConfig: [id: string, configPatch: Partial<CanvasComponentConfig>]
}>()

const richEditorRef = shallowRef<HTMLElement | null>(null)
const formatBrush = shallowRef<RichTextFormat | null>(null)
const isFullscreen = shallowRef(false)

let savedRange: Range | null = null

const fontOptions = ['Arial', 'PingFang SC', 'Microsoft YaHei', 'SimSun']
const sizeOptions = [
  { label: '12', value: '2' },
  { label: '14', value: '3' },
  { label: '18', value: '4' },
  { label: '24', value: '5' },
]
const toolbarGroups: RichTextTool[][] = [
  [
    { label: '撤销', text: '↶', command: 'undo' },
    { label: '重做', text: '↷', command: 'redo' },
  ],
  [
    { label: '清除格式', text: '⌫', command: 'removeFormat' },
    { label: '格式刷', text: '▣', command: 'copyFormat' },
  ],
  [
    { label: '加粗', text: 'B', command: 'bold' },
    { label: '斜体', text: 'I', command: 'italic' },
    { label: '下划线', text: 'U', command: 'underline' },
    { label: '删除线', text: 'S', command: 'strikeThrough' },
  ],
  [
    { label: '左对齐', text: '≡', command: 'justifyLeft' },
    { label: '项目符号', text: '•', command: 'insertUnorderedList' },
    { label: '编号列表', text: '1.', command: 'insertOrderedList' },
    { label: '缩进', text: '⇥', command: 'indent' },
  ],
  [
    { label: '插入图片', text: '▧', command: 'insertImage' },
    { label: '插入表格', text: '▦', command: 'insertTable' },
    { label: '链接', text: '⌁', command: 'createLink' },
    { label: '代码', text: '</>', command: 'inlineCode' },
  ],
  [
    { label: '签名', text: '签名', command: 'signature' },
    { label: '全屏', text: '↗', command: 'fullscreen' },
  ],
]

onMounted(() => {
  applyContentFromConfig()
})

watch(
  () => props.item.config.content,
  () => {
    applyContentFromConfig()
  },
)

function updateConfig(configPatch: Partial<CanvasComponentConfig>) {
  emit('updateConfig', props.item.id, configPatch)
}

function readInputValue(event: Event) {
  return (event.target as HTMLInputElement).value
}

function applyContentFromConfig() {
  const editor = richEditorRef.value

  if (!editor || document.activeElement === editor) {
    return
  }

  const content = props.item.config.content ?? ''

  if (editor.innerHTML !== content) {
    editor.innerHTML = content
  }
}

function saveSelection() {
  const editor = richEditorRef.value
  const selection = window.getSelection()

  if (!editor || !selection || selection.rangeCount === 0) {
    return
  }

  const range = selection.getRangeAt(0)

  if (editor.contains(range.commonAncestorContainer)) {
    savedRange = range.cloneRange()
  }
}

function restoreSelection() {
  const editor = richEditorRef.value
  const selection = window.getSelection()

  if (!editor || !selection) {
    return
  }

  editor.focus()

  if (!savedRange || !editor.contains(savedRange.commonAncestorContainer)) {
    placeCaretAtEnd(editor)
    return
  }

  selection.removeAllRanges()
  selection.addRange(savedRange)
}

function placeCaretAtEnd(editor: HTMLElement) {
  const selection = window.getSelection()
  const range = document.createRange()

  range.selectNodeContents(editor)
  range.collapse(false)
  selection?.removeAllRanges()
  selection?.addRange(range)
  savedRange = range.cloneRange()
}

function syncEditorContent() {
  const editor = richEditorRef.value

  if (!editor) {
    return
  }

  updateConfig({ content: editor.innerHTML })
}

function getSelectedTextLength() {
  const selection = window.getSelection()

  if (!selection || selection.rangeCount === 0) {
    return 0
  }

  const range = selection.getRangeAt(0)

  if (!richEditorRef.value?.contains(range.commonAncestorContainer)) {
    return 0
  }

  return range.toString().length
}

function getRemainingTextLength() {
  const maxLength = props.item.config.maxLength

  if (!maxLength || maxLength <= 0) {
    return Number.POSITIVE_INFINITY
  }

  const textLength = richEditorRef.value?.innerText.length ?? 0
  return Math.max(maxLength - textLength + getSelectedTextLength(), 0)
}

function handleBeforeInput(event: Event) {
  const inputEvent = event as InputEvent

  if (inputEvent.inputType.startsWith('delete')) {
    return
  }

  const data = inputEvent.data ?? ''

  if (data.length > getRemainingTextLength()) {
    event.preventDefault()
  }
}

function handlePaste(event: Event) {
  const clipboardEvent = event as ClipboardEvent

  event.preventDefault()
  restoreSelection()

  const text = clipboardEvent.clipboardData?.getData('text/plain') ?? ''
  const content = text.slice(0, getRemainingTextLength())

  document.execCommand('insertText', false, content)
  syncEditorContent()
  saveSelection()
}

function handleEditorInput() {
  const editor = richEditorRef.value
  const maxLength = props.item.config.maxLength

  if (editor && maxLength && maxLength > 0 && editor.innerText.length > maxLength) {
    editor.textContent = editor.innerText.slice(0, maxLength)
    placeCaretAtEnd(editor)
  }

  syncEditorContent()
  saveSelection()
}

function runToolbarCommand(command: RichTextCommand) {
  if (command === 'fullscreen') {
    isFullscreen.value = !isFullscreen.value
    return
  }

  restoreSelection()

  if (command === 'copyFormat') {
    handleFormatBrush()
    return
  }

  if (command === 'insertImage') {
    insertImage()
    return
  }

  if (command === 'insertTable') {
    document.execCommand('insertHTML', false, createTableHtml())
    finishCommand()
    return
  }

  if (command === 'createLink') {
    insertLink()
    return
  }

  if (command === 'inlineCode') {
    insertInlineCode()
    return
  }

  if (command === 'signature') {
    document.execCommand('insertHTML', false, '<p>--<br>签名</p>')
    finishCommand()
    return
  }

  document.execCommand(command, false)
  finishCommand()
}

function runSelectCommand(command: string, value: string) {
  restoreSelection()
  document.execCommand(command, false, value)
  finishCommand()
}

function runColorCommand(command: string, value: string) {
  restoreSelection()
  document.execCommand(command, false, value)
  finishCommand()
}

function handleFormatBrush() {
  if (!formatBrush.value) {
    formatBrush.value = {
      fontName: String(document.queryCommandValue('fontName') || ''),
      fontSize: String(document.queryCommandValue('fontSize') || ''),
      foreColor: String(document.queryCommandValue('foreColor') || ''),
      backColor: String(document.queryCommandValue('backColor') || ''),
      bold: document.queryCommandState('bold'),
      italic: document.queryCommandState('italic'),
      underline: document.queryCommandState('underline'),
      strikeThrough: document.queryCommandState('strikeThrough'),
    }
    return
  }

  const format = formatBrush.value

  if (format.fontName) {
    document.execCommand('fontName', false, format.fontName)
  }

  if (format.fontSize) {
    document.execCommand('fontSize', false, format.fontSize)
  }

  if (format.foreColor) {
    document.execCommand('foreColor', false, format.foreColor)
  }

  if (format.backColor) {
    document.execCommand('backColor', false, format.backColor)
  }

  applyToggleState('bold', format.bold)
  applyToggleState('italic', format.italic)
  applyToggleState('underline', format.underline)
  applyToggleState('strikeThrough', format.strikeThrough)
  formatBrush.value = null
  finishCommand()
}

function applyToggleState(command: string, enabled: boolean) {
  if (document.queryCommandState(command) !== enabled) {
    document.execCommand(command, false)
  }
}

function insertImage() {
  const imageUrl = window.prompt('请输入图片链接')

  if (!imageUrl) {
    return
  }

  document.execCommand('insertImage', false, imageUrl)
  finishCommand()
}

function insertLink() {
  const linkUrl = window.prompt('请输入链接地址')

  if (!linkUrl) {
    return
  }

  if (!window.getSelection()?.toString()) {
    document.execCommand('insertHTML', false, `<a href="${escapeHtml(linkUrl)}">${escapeHtml(linkUrl)}</a>`)
  } else {
    document.execCommand('createLink', false, linkUrl)
  }

  finishCommand()
}

function insertInlineCode() {
  const selectedText = window.getSelection()?.toString() || 'code'
  document.execCommand('insertHTML', false, `<code>${escapeHtml(selectedText)}</code>`)
  finishCommand()
}

function finishCommand() {
  syncEditorContent()
  saveSelection()
}

function createTableHtml() {
  return [
    '<table><tbody>',
    '<tr><td>单元格</td><td>单元格</td><td>单元格</td></tr>',
    '<tr><td>单元格</td><td>单元格</td><td>单元格</td></tr>',
    '<tr><td>单元格</td><td>单元格</td><td>单元格</td></tr>',
    '</tbody></table>',
  ].join('')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
</script>

<template>
  <div class="rich-text-editor" :class="{ 'rich-text-editor--fullscreen': isFullscreen }">
    <section class="rich-text-editor__panel">
      <div class="rich-text-editor__toolbar" aria-label="富文本工具栏">
        <div
          v-for="(group, groupIndex) in toolbarGroups"
          :key="groupIndex"
          class="rich-text-editor__toolbar-group"
        >
          <button
            v-for="tool in group"
            :key="tool.command"
            type="button"
            :class="{ 'rich-text-editor__tool--active': tool.command === 'copyFormat' && formatBrush }"
            :title="tool.label"
            @mousedown.prevent="runToolbarCommand(tool.command)"
          >
            {{ tool.text }}
          </button>
        </div>

        <select
          aria-label="字体"
          @mousedown="saveSelection"
          @change="runSelectCommand('fontName', readInputValue($event))"
        >
          <option v-for="font in fontOptions" :key="font" :value="font">{{ font }}</option>
        </select>

        <select
          aria-label="字号"
          @mousedown="saveSelection"
          @change="runSelectCommand('fontSize', readInputValue($event))"
        >
          <option v-for="size in sizeOptions" :key="size.value" :value="size.value">{{ size.label }}</option>
        </select>

        <label class="rich-text-editor__color" title="文字颜色" @mousedown="saveSelection">
          A
          <input type="color" value="#1d1d1f" @input="runColorCommand('foreColor', readInputValue($event))">
        </label>

        <label class="rich-text-editor__color" title="背景颜色" @mousedown="saveSelection">
          ◆
          <input type="color" value="#fff4a3" @input="runColorCommand('backColor', readInputValue($event))">
        </label>
      </div>

      <div
        ref="richEditorRef"
        class="rich-text-editor__body"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        :data-placeholder="item.config.placeholder || '请输入详情内容'"
        @beforeinput="handleBeforeInput"
        @input="handleEditorInput"
        @focus="saveSelection"
        @keyup="saveSelection"
        @mouseup="saveSelection"
        @paste="handlePaste"
      ></div>
    </section>
  </div>
</template>

<style scoped>
.rich-text-editor {
  display: grid;
  min-width: 0;
}

.rich-text-editor--fullscreen {
  position: fixed;
  z-index: 60;
  inset: 18px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.96);
  padding: 18px;
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.2);
}

.rich-text-editor__panel {
  display: grid;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--lh-border-soft);
  border-radius: 12px;
  background: #ffffff;
}

.rich-text-editor__toolbar {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: 7px;
  overflow-x: auto;
  border-bottom: 1px solid var(--lh-border-soft);
  background: rgba(248, 250, 252, 0.94);
  padding: 7px 9px;
}

.rich-text-editor__toolbar-group {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 3px;
  padding-right: 7px;
  border-right: 1px solid rgba(23, 32, 51, 0.08);
}

.rich-text-editor__toolbar button,
.rich-text-editor__toolbar select,
.rich-text-editor__color {
  flex: 0 0 auto;
  height: 28px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--lh-text-secondary);
  font-size: 12px;
  font-weight: 750;
}

.rich-text-editor__toolbar button,
.rich-text-editor__color {
  display: grid;
  min-width: 28px;
  place-items: center;
  cursor: pointer;
  padding: 0 7px;
}

.rich-text-editor__toolbar button:hover,
.rich-text-editor__color:hover,
.rich-text-editor__tool--active {
  border-color: rgba(0, 113, 227, 0.18);
  background: var(--lh-accent-soft);
  color: var(--lh-accent);
}

.rich-text-editor__toolbar select {
  min-width: 72px;
  background: rgba(255, 255, 255, 0.8);
  padding: 0 8px;
}

.rich-text-editor__color {
  position: relative;
  overflow: hidden;
}

.rich-text-editor__color input {
  position: absolute;
  inset: 0;
  cursor: pointer;
  opacity: 0;
}

.rich-text-editor__body {
  min-height: 210px;
  outline: none;
  overflow: auto;
  background: #ffffff;
  color: var(--lh-text-primary);
  font-size: 14px;
  line-height: 1.7;
  padding: 16px;
}

.rich-text-editor--fullscreen .rich-text-editor__body {
  min-height: calc(100vh - 170px);
}

.rich-text-editor__body:empty::before {
  color: var(--lh-text-tertiary);
  content: attr(data-placeholder);
  pointer-events: none;
}

.rich-text-editor__body :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.rich-text-editor__body :deep(td) {
  min-width: 72px;
  border: 1px solid var(--lh-border-soft);
  padding: 8px;
}

.rich-text-editor__body :deep(code) {
  border-radius: 5px;
  background: rgba(23, 32, 51, 0.08);
  color: #b42318;
  padding: 2px 5px;
}

.rich-text-editor__body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

</style>
