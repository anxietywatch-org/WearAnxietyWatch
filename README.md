# AnxietyWatch

Base del monorepositorio para una plataforma de bienestar y apoyo conectada a Samsung Galaxy Watch7. AnxietyWatch detectará cambios fisiológicos inusuales, los contextualizará con actividad física y ofrecerá intervención; no diagnostica ansiedad ni confirma clínicamente crisis.

## Estado de esta entrega

Incluye únicamente los fundamentos:

- Aplicación Wear OS compilable en `apps/wear`.
- Aplicación React Native Android en `apps/mobile`.
- Dashboard React + Vite en `apps/web`.
- API Express 5 con `GET /health` en `services/api`.
- Servicio FastAPI con `GET /health` en `services/ml`.
- Contratos TypeScript/Zod en `packages/contracts`.
- PostgreSQL local mediante Docker Compose.
- OpenAPI, pruebas, lint, formato y CI.

No incluye todavía sensores Samsung, Data Layer, detección, autenticación, SOS, notificaciones ni modelos ML.

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
infrastructure/
  postgres/   Inicialización reproducible local
docs/
  architecture, api, product, security
```

## Requisitos locales

- Node.js 22.12 o posterior.
- npm 11 o posterior.
- Android Studio, Android SDK y JDK 17.
- Python 3.11 o posterior.
- Docker Desktop para PostgreSQL.

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
docker compose up -d postgres
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

## Manejo de errores y seguridad

- Todo dato externo se valida en los límites del sistema.
- Un sensor ausente se representa como `unsupported` o `unavailable`.
- No se guardan contraseñas, tokens, coordenadas ni telemetría en logs.
- La ubicación no se solicitará hasta que exista consentimiento y un flujo SOS.
- EDA permanece no compatible para Galaxy Watch7.

## Criterios de aceptación de la base

- Los tres clientes tienen una entrada compilable.
- Ambos servicios responden en `/health`.
- Los contratos rechazan lotes de telemetría inválidos.
- PostgreSQL inicia con una migración base reproducible.
- Las pruebas y la CI cubren cada tecnología.

## Próxima entrega

Implementar la primera prueba vertical: `FakeSensorProvider` en el reloj → Wearable Data Layer → móvil → `POST /api/v1/telemetry/batch` idempotente → PostgreSQL → consulta en web.

Consulta [la arquitectura](docs/architecture/README.md) y [el contrato OpenAPI](docs/api/openapi.yaml).
