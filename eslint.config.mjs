import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['**/build/**', '**/dist/**', '**/node_modules/**'] },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: [
      'apps/web/src/**/*.{ts,tsx}',
      'apps/mobile/App.{ts,tsx}',
      'apps/mobile/index.js',
      'apps/mobile/src/**/*.{ts,tsx}',
      'services/api/src/**/*.ts',
      'packages/contracts/src/**/*.ts',
    ],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_' },
      ],
    },
  },
);
