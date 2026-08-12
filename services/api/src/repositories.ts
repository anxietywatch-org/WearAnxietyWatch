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
 * Persistencia sobre SQLite (node:sqlite, sin dependencias nativas).
 *
 * En producción se debe fijar FOG_DB_PATH; si no está definido se usa una base
 * en memoria (adecuado para desarrollo y pruebas). Las interfaces de repositorio
 * se mantienen para poder migrar a MongoDB Atlas sin tocar las rutas.
 */
export class SqliteTelemetryRepository implements TelemetryRepository {
  private readonly db: DatabaseSync;
  private readonly insert: ReturnType<DatabaseSync['prepare']>;

  constructor(path: string) {
    this.db = new DatabaseSync(path);
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS telemetry_batches (
        batch_id TEXT PRIMARY KEY,
        payload TEXT NOT NULL,
        received_at INTEGER NOT NULL
      )
    `);
    this.insert = this.db.prepare(
      'INSERT OR IGNORE INTO telemetry_batches (batch_id, payload, received_at) VALUES (?, ?, ?)',
    );
  }

  saveIfNew(batch: TelemetryBatch): boolean {
    const result = this.insert.run(batch.batchId, JSON.stringify(batch), Date.now());
    return result.changes > 0;
  }

  close(): void {
    this.db.close();
  }
}

export class SqliteSosEventRepository implements SosEventRepository {
  private readonly db: DatabaseSync;
  private readonly insertEvent: ReturnType<DatabaseSync['prepare']>;
  private readonly insertCancel: ReturnType<DatabaseSync['prepare']>;

  constructor(path: string) {
    this.db = new DatabaseSync(path);
    this.db.exec(`
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
    this.insertEvent = this.db.prepare(
      'INSERT OR IGNORE INTO sos_events (event_id, payload, received_at) VALUES (?, ?, ?)',
    );
    this.insertCancel = this.db.prepare(
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

  close(): void {
    this.db.close();
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

export const telemetryRepository = new SqliteTelemetryRepository(dbPath);
export const sosEventRepository = new SqliteSosEventRepository(dbPath);
export const pushNotifier: PushNotifier = process.env.CAREGIVER_WEBHOOK_URL
  ? new WebhookPushNotifier()
  : new LoggingPushNotifier();

// The support-network repository is not available in this delivery yet.
export const acceptedCaregiversFor = (_userId: string): string[] => [];
