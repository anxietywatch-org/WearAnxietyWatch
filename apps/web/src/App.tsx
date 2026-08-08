import './App.css';

export const dashboardSummary = {
  status: 'Sin eventos activos',
  device: 'Galaxy Watch7 pendiente de vinculación',
  sync: 'Sin datos recibidos',
} as const;

function App() {
  return (
    <main className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">ANXIETYWATCH · BASE DEL SISTEMA</p>
          <h1>Monitoreo y apoyo, bajo control del usuario.</h1>
          <p className="lead">
            Dashboard inicial del ecosistema conectado para Galaxy Watch7.
          </p>
        </div>
        <span className="status-pill">Fundamentos listos</span>
      </header>

      <section className="grid" aria-label="Estado del sistema">
        <article className="card card--healthy">
          <span className="card__label">Estado general</span>
          <strong>{dashboardSummary.status}</strong>
          <small>El motor de detección aún no está habilitado.</small>
        </article>
        <article className="card">
          <span className="card__label">Dispositivo</span>
          <strong>{dashboardSummary.device}</strong>
          <small>EDA no forma parte del perfil de Watch7.</small>
        </article>
        <article className="card">
          <span className="card__label">Última sincronización</span>
          <strong>{dashboardSummary.sync}</strong>
          <small>
            La prueba vertical de telemetría será la siguiente entrega.
          </small>
        </article>
      </section>

      <aside className="notice">
        AnxietyWatch es una herramienta de bienestar y apoyo. La información
        mostrada no representa un diagnóstico médico.
      </aside>
    </main>
  );
}

export default App;
