# AnxietyWatch

Plataforma de bienestar y apoyo conectada a Samsung Galaxy Watch7. AnxietyWatch detecta cambios fisiológicos inusuales, los contextualiza con actividad física y ofrece intervención; no diagnostica ansiedad ni confirma clínicamente crisis.

## Estado de esta entrega

Capa fog funcional de punta a punta:

- **Reloj (Wear OS, `apps/wear`)**: monitoreo con detección preliminar, SOS manual/cancelación, cola de salida con reintentos y confirmación por identificador. No hace HTTP: entrega sobres al teléfono por Wear Data Layer (`/fog/v1/...`, protocolo `fog_watch_v1`).
- **Teléfono como nodo fog (`apps/mobile`, React Native)**: inicia sesión contra `https://api.mangoon.xyz`, conserva el JWT cifrado con Android Keystore, recibe sobres aun sin interfaz abierta mediante Headless JS, los persiste en **Room/SQLite**, los enriquece y los entrega al API (protocolo `fog_phone_v1`). Confirma solo tras aceptación del cloud (`202`/`200`) y reenvía con backoff. El ACK al reloj es por identificador: `/fog/v1/ack/telemetry/{batchId}`, `/fog/v1/ack/sos/{eventId}`, `/fog/v1/ack/sos-cancel/{eventId}`.
- **API de referencia local (`services/api`, Express 5)**: sirve para contratos y pruebas aisladas. El destino real del móvil es el backend oficial .NET desplegado en `https://api.mangoon.xyz`, que autentica cuentas AnxietyWatch y persiste en MongoDB Atlas.
- **Servicio ML (`services/ml`, FastAPI)**: con `GET /health`; sin modelos en esta fase.
- **Dashboard (`apps/web`, React + Vite)**: estático por ahora (no consume el API todavía).
- **Contratos (`packages/contracts`)**: esquemas Zod compartidos.
- **CI**: jobs para node, wear, mobile-android y ml (`docs/DEVOPS.md`).

## Estructura

```text
apps/
  wear/       Wear OS + Kotlin + Compose
  mobile/     React Native + TypeScript
  web/        React + Vite + TypeScript
services/
  api/        Express + TypeScript
  ml/         FastAPI; sin modelos en esta fase
packages/
  contracts/  Esquemas compartidos y tipos
docs/
  architecture, api, product, security, DEVOPS
```

## Requisitos locales

- Node.js 22.18 o posterior (ejecución nativa de TypeScript).
- npm 11 o posterior.
- Android Studio, Android SDK y JDK 17.
- Python 3.11 o posterior.
- Docker Desktop solo si se quiere el PostgreSQL opcional (el API actual persiste en SQLite y no lo necesita).

## Preparación

```powershell
Copy-Item .env.example .env
npm.cmd install
```

Para Python:

```powershell
Set-Location services\ml
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
```

## Comandos principales

```powershell
npm.cmd run build
npm.cmd run test
npm.cmd run lint
```

Wear OS:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :apps:wear:assembleDebug
```

React Native Android:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
Set-Location apps\mobile\android
.\gradlew.bat assembleDebug
```

## Variables de entorno (API)

- `API_PORT`: puerto HTTP, `3000` por defecto.
- `API_AUTH_TOKENS`: CSV de tokens Bearer válidos (401 sin header, 403 token inválido).
- `FOG_DB_PATH`: ruta del archivo SQLite (sin definir, `:memory:`).
- `CAREGIVER_WEBHOOK_URL`: endpoint de notificación SOS (sin definir, solo log).

## Manejo de errores y seguridad

- Todo dato externo se valida en los límites del sistema (zod en el API).
- No se guardan contraseñas, tokens, coordenadas ni telemetría en logs.
- La ubicación no se solicitará hasta que exista consentimiento y un flujo SOS.
- EDA permanece no compatible para Galaxy Watch7.

## Criterios de aceptación

- El reloj entrega telemetría y eventos SOS/cancelación al teléfono por Wear Data Layer y confirma por identificador.
- El móvil encola en Room, entrega al API y solo completa tras el ACK del reloj.
- El API persiste en SQLite, deduplica por `batchId`/`eventId`, autentica con Bearer y responde 400/401/403/404/413/202/200 correctamente.
- Las pruebas y la CI cubren cada tecnología.

## Limitaciones conocidas

- Si Android fuerza la detención manual de la app, el sistema no puede despertarla hasta que el usuario vuelva a abrirla; durante ese intervalo el reloj conserva sus sobres para reintentarlos.
- El web es un dashboard estático sin capa de red.
- `acceptedCaregiversFor` del API es un stub: el webhook SOS se dispara sin lista de cuidadores.
- La autenticación valida la pertenencia del token a `API_AUTH_TOKENS`; la vinculación token↔usuario vendrá con el servicio de identidad futuro.

Consulta [la arquitectura](docs/architecture/README.md) y [el contrato OpenAPI](docs/api/openapi.yaml).
