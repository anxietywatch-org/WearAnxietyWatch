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

- `MessageClient`: eventos inmediatos y respuestas.
- `DataClient`: estados y lotes pequeños sincronizables.
- HTTPS: teléfono a API.
- PostgreSQL: persistencia central.

El canal principal reloj-teléfono será Wearable Data Layer, no BLE directo.

## Offline first

Cada mensaje persistible tendrá identificador estable, secuencia y estado de entrega. Telemetría, eventos y SOS aceptarán claves de idempotencia para tolerar reintentos.

## Versionado

Los eventos deberán registrar `ruleVersion`, `featureSchemaVersion` y, cuando exista, `modelVersion`. Los umbrales no vivirán duplicados en la interfaz.

## Decisiones pendientes

- Elegir Prisma o Drizzle después de la prueba de carga inicial.
- Validar el SDK de sensores en un Watch7 físico y el programa de socios Samsung.
- Definir la política legal de retención antes de almacenar datos reales.
