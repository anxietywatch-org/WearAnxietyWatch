# AnxietyWatch Wear OS

Aplicación autónoma para Galaxy Watch7 orientada a bienestar y apoyo. No diagnostica ansiedad ni confirma crisis.

## Funciones implementadas

- Frecuencia cardíaca real mediante Wear OS Health Services mientras la interfaz está activa.
- Monitoreo pasivo en segundo plano con `PassiveListenerService` y restauración tras reinicio.
- Acelerómetro mediante `SensorManager`, agregado en ventanas de un segundo.
- `FakeSensorProvider` con escenarios normal y de anomalía.
- Adaptador explícito para Samsung Health Sensor SDK sin inventar ni incluir su AAR propietario.
- Detección de capacidades y estados `available`, `unavailable`, `unsupported` y `permission_required`.
- Búfer de 30 minutos, línea base incremental, extracción de HR/HRV/movimiento y detector por reglas versionadas.
- Máquina de estados, clasificación de falso positivo y periodo de enfriamiento.
- Respiración háptica 4–4, grounding 5–4–3–2–1 y SOS con pulsación prolongada más cuenta regresiva cancelable.
- SQLite local para telemetría, eventos, línea base y cola offline.
- Data Layer únicamente del lado del reloj; los datos permanecen pendientes si no existe un teléfono compatible.

## Preparación

Abre el proyecto raíz en Android Studio y selecciona el módulo `apps:wear`.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :apps:wear:assembleDebug
.\gradlew.bat :apps:wear:testDebugUnitTest
```

En el primer inicio, la app explica y solicita frecuencia cardíaca, reconocimiento de actividad, notificaciones y acceso en segundo plano. Si se niegan, respiración, grounding y SOS local continúan disponibles.

## Samsung Health Sensor SDK

Samsung distribuye `samsung-health-sensor-api.aar` por separado. Para habilitar IBI, PPG o temperatura se requiere:

1. Descargar el SDK desde Samsung Developer.
2. Colocar el AAR bajo `apps/wear/libs/`.
3. Implementar el puente tipado de `SamsungSensorProvider` con la versión real del SDK.
4. Probar con Health Platform Developer Mode sólo durante desarrollo.
5. Registrar `com.anxietywatch.wear` y la firma SHA-256 de producción en el programa de socios Samsung.

Hasta completar esos pasos, IBI, PPG y temperatura aparecen como no disponibles. EDA siempre aparece como no compatible para Galaxy Watch7.

## Arquitectura

- `sensor/`: proveedores reales, simulados, permisos y monitoreo pasivo.
- `monitoring/`: búfer, características, reglas y máquina de estados.
- `storage/`: SQLite y cola offline.
- `datalayer/`: observación de conexión y envío watch-side.
- `intervention/`: vibración y avisos.
- `presentation/`: pantallas Compose basadas en el prototipo OLED.
- `runtime/`: orquestación y estado observable.

## Manejo de errores y privacidad

- Un sensor ausente nunca cierra la aplicación.
- No se registran coordenadas, secretos ni datos biométricos en Logcat.
- La telemetría queda local y pendiente hasta que exista un par Data Layer compatible.
- Un cambio fisiológico abre una comprobación; no genera una afirmación clínica.
- La falta de respuesta no envía SOS automáticamente.

## Criterios de aceptación

- Compila un APK Wear OS.
- Los permisos negados se representan en la interfaz.
- El simulador permite calibrar y reproducir una anomalía.
- Las reglas requieren múltiples señales y se prueban sin EDA.
- La respiración vibra y puede detenerse.
- El SOS requiere dos acciones y puede cancelarse.
- Los datos sobreviven al cierre del proceso mediante SQLite.

## Limitaciones conocidas

- La lectura física de IBI, PPG y temperatura requiere el AAR propietario y el alta de socio Samsung.
- El emulador no reproduce Samsung Health Sensor SDK; usa el proveedor simulado o los datos sintéticos de Health Services.
- Data Layer exige que reloj y teléfono usen el mismo package name y firma. No se modificó la aplicación móvil en esta entrega.
- Los umbrales son configuración experimental de bienestar, no valores clínicos universales.
