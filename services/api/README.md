# AnxietyWatch API

API Express 5 en TypeScript estricto. Esta entrega solo expone `GET /health`; los endpoints de dominio se añadirán por módulos.

## Variables de entorno

- `API_PORT`: puerto HTTP, `3000` por defecto.

## Comandos

- `npm run build --workspace @anxietywatch/api`
- `npm run test --workspace @anxietywatch/api`
- `npm run dev --workspace @anxietywatch/api`

## Arquitectura y errores

`app.ts` configura el límite HTTP y el identificador de correlación. `server.ts` controla el ciclo de vida del proceso. Los errores no exponen información sensible.

## Criterios de aceptación

- `/health` devuelve HTTP 200 y una respuesta tipada.
- Las rutas desconocidas devuelven HTTP 404.
- La compilación usa TypeScript estricto y no utiliza `any` explícito.

## Limitaciones conocidas

Todavía no existe persistencia ni autenticación. Se incorporarán después de cerrar los contratos e introducir la primera migración reproducible.
