# AnxietyWatch Web

Aplicación React + Vite para los futuros dashboards de paciente y cuidador.

## Variables de entorno

- `VITE_API_BASE_URL`: URL del API, `http://localhost:3000` en desarrollo.

## Comandos

- `npm run dev --workspace @anxietywatch/web`
- `npm run build --workspace @anxietywatch/web`
- `npm run test --workspace @anxietywatch/web`

## Arquitectura y errores

La primera pantalla solo comunica el estado de integración. Los datos futuros deberán llegar mediante clientes validados por los contratos compartidos; nunca se mostrará una alerta entregada sin confirmación del backend.

## Criterios de aceptación

- TypeScript estricto y Vite generan el bundle de producción.
- El diseño responde en escritorio y móvil.
- El aviso de bienestar es visible.

## Limitaciones conocidas

No hay autenticación, rutas ni datos en tiempo real. El dashboard del cuidador se implementará después del endpoint de telemetría.
