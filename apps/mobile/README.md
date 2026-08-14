# AnxietyWatch Mobile

Aplicación React Native 0.84 para Android que funciona como nodo fog entre el
Galaxy Watch y el backend oficial.

La app inicia sesión en `https://api.mangoon.xyz`, guarda el JWT cifrado con
Android Keystore y restaura/renueva la sesión mediante `/api/auth/session`. Los
sobres recibidos por Wear Data Layer quedan en Room y una tarea Headless JS
intenta entregarlos aunque la interfaz no esté abierta. Android no permite
despertar una app que el usuario haya detenido de forma forzada; el reloj
conserva los sobres durante ese caso.

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
- El HTTP autenticado solo se ejecuta con JavaScript activo; Room retiene el
  sobre y la tarea Headless reanuda el intento.
