from fastapi import FastAPI

app = FastAPI(
    title="AnxietyWatch ML",
    version="0.1.0",
    description="Health-only scaffold. No diagnostic model is enabled.",
)


@app.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "anxietywatch-ml",
        "version": "0.1.0",
    }
