import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import prettierSkipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default [
  {
    name: 'labelhub/files',
    files: ['**/*.{ts,vue}'],
  },
  {
    name: 'labelhub/ignores',
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  ...pluginVue.configs['flat/recommended'],
  ...vueTsEslintConfig(),
  prettierSkipFormatting,
]
