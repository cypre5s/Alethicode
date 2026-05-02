import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.jest,
        ResizeObserver: 'readonly',
        __APP_VERSION__: 'readonly',
        __APP_DEV__: 'readonly',
        __APP_PROD__: 'readonly',
      },
    },
    rules: {
      'arrow-parens': 'off',
      'generator-star-spacing': 'off',
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
      'no-irregular-whitespace': ['error', {
        skipComments: true,
        skipTemplates: true,
      }],
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      'space-before-function-paren': 'off',
      'comma-dangle': 'off',
      'one-var': 'off',
      'no-mixed-operators': 'off',
      'no-useless-escape': 'off',
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-prototype-builtins': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-reserved-keys': 'off',
      'vue/no-reserved-component-names': 'off',
    },
  },
  {
    ignores: ['dist/', 'node_modules/', 'tests/'],
  },
]
