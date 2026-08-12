# Arquitectura inicial

## Límites

1. **Reloj:** adquisición, búfer, detección preliminar, interfaz inmediata e intervención háptica.
2. **Teléfono:** coordinación local, línea base, reglas, cola offline, ubicación consentida y puente con backend.
3. **API:** identidad, autorización, telemetría, eventos, SOS, auditoría y notificaciones.
4. **Web:** lectura autorizada del estado e historial.
5. **ML:** evaluación futura con datos etiquetados; no participa en la primera ruta vertical.

## Dependencias permitidas

```text
UI -> casos de uso -> contratos/puertos -> adaptadores
```

La detección nunca importa directamente el SDK de Samsung. `SamsungSensorProvider`, `HealthServicesProvider` y `FakeSensorProvider` implementarán el mismo puerto.

## Comunicación

- `MessageClient`: eventos inmediatos y respuestas (SOS y su cancelación).
- `DataClient`: telemetría en lotes y el anuncio de capabilities del reloj.
- HTTPS: teléfono (nodo fog) a API, únicamente.
- SQLite (`node:sqlite`): persistencia central del API en esta fase (migración a MongoDB Atlas detrás de las mismas interfaces de repositorio).
- Room/SQLite: cola de salida del nodo fog en el teléfono.

El canal principal reloj-teléfono es Wearable Data Layer, no BLE directo. El reloj no realiza HTTP: entrega sobres en `/fog/v1/*` (protocolo `fog_watch_v1`) y el teléfono los enriquece (`fog_phone_v1`) antes de llamar al API. Las confirmaciones vuelven al reloj por identificador (`/fog/v1/ack/...`).

## Offline first

Cada mensaje persistible tendrá identificador estable, secuencia y estado de entrega. Telemetría, eventos y SOS aceptarán claves de idempotencia para tolerar reintentos.

## Versionado

Los eventos deberán registrar `ruleVersion`, `featureSchemaVersion` y, cuando exista, `modelVersion`. Los umbrales no vivirán duplicados en la interfaz.

## Decisiones pendientes

- Elegir Prisma o Drizzle después de la prueba de carga inicial.
- Validar el SDK de sensores en un Watch7 físico y el programa de socios Samsung.
- Definir la política legal de retención antes de almacenar datos reales.
