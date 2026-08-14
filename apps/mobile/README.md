# AnxietyWatch Mobile

Aplicación React Native 0.84 para Android que funciona como nodo fog entre el
Galaxy Watch y el backend oficial.

La app inicia sesión en `https://api.mangoon.xyz`, guarda el JWT y la identidad
fog cifrados con la clave Android Keystore `anxietywatch_fog_v1`, y restaura la
sesión mediante `/api/auth/session`. Los sobres de Wear Data Layer quedan en
Room y un `CoroutineWorker` Kotlin los entrega sin depender de React Native. El
trabajo único `fog-sync` exige conectividad, usa backoff y sólo elimina la fila
después del ACK real al reloj.

## Variables de entorno

La plantilla no requiere secretos. Las URL y credenciales futuras se documentarán en `.env.example` y nunca se guardarán en el repositorio.

## Comandos

- `npm run start --workspace @anxietywatch/mobile`
- `npm run android --workspace @anxietywatch/mobile`
- `npm run test --workspace @anxietywatch/mobile`
- `npm run lint --workspace @anxietywatch/mobile`

Para compilar Android sin iniciar un emulador:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
Set-Location apps\mobile\android
.\gradlew.bat assembleDebug
```

## Arquitectura y manejo de errores

Los módulos futuros vivirán bajo `src/` por dominio. El módulo nativo de Wearable Data Layer será un adaptador, no una dependencia directa de la detección. Las colas offline deben ser idempotentes.

## Criterios de aceptación

- El bundle TypeScript y la aplicación Android compilan.
- La pantalla inicial indica que el reloj aún no está vinculado.
- El texto visible no afirma diagnósticos.

## Vínculo con el reloj (Wear Data Layer)

Wear Data Layer exige que el teléfono y el reloj compartan `applicationId` y
certificado de firma. Ambos usan `com.anxietywatch.wear`; las builds debug
firman con la clave predeterminada de Android (`~/.android/debug.keystore`).
Las builds release deben firmarse con la misma clave privada desde el almacén
seguro de CI, o el par no se vinculará.

## Limitaciones conocidas

- Android no permite despertar una app que el usuario detuvo de forma forzada;
  en ese caso el reloj conserva los sobres (colas idempotentes en su base local).
- Android no ejecuta trabajo si el usuario fuerza la detención; Room y la cola
  del reloj conservan los sobres hasta que la app pueda reiniciarse.
