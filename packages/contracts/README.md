# @anxietywatch/contracts

Contratos compartidos y validación de límites externos para reloj, móvil, web y API.

## Comandos

- `npm run build --workspace @anxietywatch/contracts`
- `npm run test --workspace @anxietywatch/contracts`

## Arquitectura y manejo de errores

Los esquemas Zod validan datos no confiables antes de que entren al dominio. Los sensores ausentes se expresan como `unsupported` o `unavailable`; nunca se inventan valores.

## Criterios de aceptación

- TypeScript estricto compila sin `any` explícito.
- EDA puede declararse no compatible para Galaxy Watch7.
- Los lotes vacíos o con fechas inválidas se rechazan.

## Limitaciones conocidas

Esta primera entrega define contratos base. El versionado y la compatibilidad retroactiva se ampliarán cuando exista la prueba vertical de telemetría.
