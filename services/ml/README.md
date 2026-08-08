# AnxietyWatch ML

Servicio FastAPI preparado para las fases futuras de evaluación y versionado de modelos. En esta entrega no entrena ni ejecuta modelos clínicos.

## Variables de entorno

- `ML_PORT`: puerto local sugerido, `8000` por defecto.

## Comandos

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
.\.venv\Scripts\python -m pytest
.\.venv\Scripts\python -m uvicorn anxietywatch_ml.main:app --app-dir src --reload
```

## Arquitectura y errores

La aplicación vive en `src/anxietywatch_ml`. Cualquier modelo futuro deberá ser intercambiable, versionado y evaluado frente a las reglas configurables.

## Criterios de aceptación

- `/health` devuelve HTTP 200.
- El módulo se instala como paquete Python.
- No existe inferencia diagnóstica en esta fase.

## Limitaciones conocidas

No hay dataset, entrenamiento ni endpoint de predicción. Estas funciones están deliberadamente fuera de la primera entrega.
