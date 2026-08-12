import { DatabaseSync } from 'node:sqlite';
import type { SosCancel, SosTrigger, TelemetryBatch } from '@anxietywatch/contracts';

export interface TelemetryRepository {
  saveIfNew(batch: TelemetryBatch): boolean;
}

export interface SosEventRepository {
  saveIfNew(event: SosTrigger): boolean;
  cancel(event: SosCancel): boolean;
}

export interface PushNotifier {
  notifyCaregivers(event: SosTrigger, caregiverIds: string[]): Promise<void>;
}

/**
 * Una única conexión SQLite compartida entre los repositorios: evita
 * SQLITE_BUSY entre dos conexiones al mismo archivo. WAL + busy_timeout para
 * lecturas concurrentes. En producción se fija FOG_DB_PATH; sin él, :memory:.
 */
class SharedDatabase {
  readonly db: DatabaseSync;
  private closed = false;

  constructor(path: string) {
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA journal_mode = WAL');
    this.db.exec('PRAGMA busy_timeout = 5000');
  }

  close(): void {
    if (this.closed) return;
    this.closed = true;
    this.db.close();
  }
}

export class SqliteTelemetryRepository implements TelemetryRepository {
  private readonly insert: ReturnType<DatabaseSync['prepare']>;

  constructor(shared: SharedDatabase) {
    shared.db.exec(`
      CREATE TABLE IF NOT EXISTS telemetry_batches (
        batch_id TEXT PRIMARY KEY,
        payload TEXT NOT NULL,
        received_at INTEGER NOT NULL
      )
    `);
    this.insert = shared.db.prepare(
      'INSERT OR IGNORE INTO telemetry_batches (batch_id, payload, received_at) VALUES (?, ?, ?)',
    );
  }

  saveIfNew(batch: TelemetryBatch): boolean {
    const result = this.insert.run(batch.batchId, JSON.stringify(batch), Date.now());
    return result.changes > 0;
  }
}

export class SqliteSosEventRepository implements SosEventRepository {
  private readonly insertEvent: ReturnType<DatabaseSync['prepare']>;
  private readonly insertCancel: ReturnType<DatabaseSync['prepare']>;

  constructor(shared: SharedDatabase) {
    shared.db.exec(`
      CREATE TABLE IF NOT EXISTS sos_events (
        event_id TEXT PRIMARY KEY,
        payload TEXT NOT NULL,
        received_at INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS sos_cancels (
        event_id TEXT PRIMARY KEY,
        payload TEXT NOT NULL,
        received_at INTEGER NOT NULL
      );
    `);
    this.insertEvent = shared.db.prepare(
      'INSERT OR IGNORE INTO sos_events (event_id, payload, received_at) VALUES (?, ?, ?)',
    );
    this.insertCancel = shared.db.prepare(
      'INSERT OR IGNORE INTO sos_cancels (event_id, payload, received_at) VALUES (?, ?, ?)',
    );
  }

  saveIfNew(event: SosTrigger): boolean {
    const result = this.insertEvent.run(event.eventId, JSON.stringify(event), Date.now());
    return result.changes > 0;
  }

  cancel(event: SosCancel): boolean {
    const result = this.insertCancel.run(event.eventId, JSON.stringify(event), Date.now());
    return result.changes > 0;
  }
}

export class LoggingPushNotifier implements PushNotifier {
  async notifyCaregivers(
    event: SosTrigger,
    caregiverIds: string[],
  ): Promise<void> {
    console.info(
      JSON.stringify({
        level: 'info',
        message: 'SOS notification requested',
        eventId: event.eventId,
        userId: event.userId,
        caregiverIds,
      }),
    );
  }
}

/**
 * Notifica a los cuidadores mediante un webhook (CAREGIVER_WEBHOOK_URL).
 * Si la variable no está definida, degrada a LoggingPushNotifier para no
 * perder la trazabilidad de la alerta.
 */
export class WebhookPushNotifier implements PushNotifier {
  private readonly webhookUrl = process.env.CAREGIVER_WEBHOOK_URL;
  private readonly fallback = new LoggingPushNotifier();

  async notifyCaregivers(
    event: SosTrigger,
    caregiverIds: string[],
  ): Promise<void> {
    if (!this.webhookUrl) {
      await this.fallback.notifyCaregivers(event, caregiverIds);
      return;
    }
    if (caregiverIds.length === 0) {
      console.warn(
        JSON.stringify({
          level: 'warn',
          message: 'SOS webhook sin cuidadores registrados para el usuario',
          eventId: event.eventId,
          userId: event.userId,
        }),
      );
    }
    try {
      const response = await fetch(this.webhookUrl, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ event, caregiverIds }),
        signal: AbortSignal.timeout(5_000),
      });
      if (!response.ok) {
        console.warn(
          JSON.stringify({
            level: 'warn',
            message: 'SOS webhook returned non-2xx',
            status: response.status,
            eventId: event.eventId,
          }),
        );
      }
    } catch (error) {
      console.error(
        JSON.stringify({
          level: 'error',
          message: 'SOS webhook delivery failed',
          eventId: event.eventId,
          detail: error instanceof Error ? error.message : String(error),
        }),
      );
    }
  }
}

const dbPath = process.env.FOG_DB_PATH ?? ':memory:';

/**
 * La conexión SQLite se abre al importar el módulo. Si FOG_DB_PATH apunta a un
 * directorio inexistente o no escribible, el fallo ocurre aquí con el mensaje
 * de Node, antes de que el servidor escuche.
 */
export const sharedDatabase = new SharedDatabase(dbPath);
export const telemetryRepository = new SqliteTelemetryRepository(sharedDatabase);
export const sosEventRepository = new SqliteSosEventRepository(sharedDatabase);
export const pushNotifier: PushNotifier = process.env.CAREGIVER_WEBHOOK_URL
  ? new WebhookPushNotifier()
  : new LoggingPushNotifier();

// The support-network repository is not available in this delivery yet.
export const acceptedCaregiversFor = (_userId: string): string[] => [];