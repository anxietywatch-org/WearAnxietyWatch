# AnxietyWatch API

API Express 5 en TypeScript estricto. Expone `/health` y las rutas fog `/api/v1/*` (telemetría, SOS y cancelación de SOS).

## Variables de entorno

- `API_PORT`: puerto HTTP, `3000` por defecto.
- `API_AUTH_TOKENS`: lista CSV de tokens Bearer válidos para `/api/v1/*`. Sin este header el API responde 401; con un token no listado, 403.
- `FOG_DB_PATH`: ruta de la base SQLite. Si no está definida se usa `:memory:` (datos no persistidos entre reinicios).
- `CAREGIVER_WEBHOOK_URL`: endpoint al que se hace POST al activarse un SOS. Si no está definida, la notificación se registra en el log.

## Comandos

- `npm run build --workspace @anxietywatch/api`
- `npm run test --workspace @anxietywatch/api`
- `npm run dev --workspace @anxietywatch/api`

## Arquitectura y errores

`app.ts` configura el límite HTTP, el identificador de correlación y la autenticación Bearer. `repositories.ts` implementa la persistencia sobre SQLite (node:sqlite) bajo interfaces intercambiables (migración a MongoDB Atlas sin tocar las rutas). `server.ts` controla el ciclo de vida del proceso. Los errores no exponen información sensible.

## Criterios de aceptación

- `/health` devuelve HTTP 200 y una respuesta tipada.
- `/api/v1/*` requiere un token Bearer válido de `API_AUTH_TOKENS` (401/403).
- Las rutas desconocidas devuelven HTTP 404.
- La compilación usa TypeScript estricto y no utiliza `any` explícito.

## Limitaciones conocidas

- El nodo fog móvil depende del runtime de React Native: con la app cerrada el reloj no puede entregar sobres hasta que la app se abra.
- Las rutas `/api/v1/*` reciben la telemetría deduplicada por `batchId` y los eventos SOS por `eventId`, pero aún no hay consultas de lectura para el dashboard.
