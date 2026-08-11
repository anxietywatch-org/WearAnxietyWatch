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

export class InMemoryTelemetryRepository implements TelemetryRepository {
  private readonly batches = new Map<string, TelemetryBatch>();

  saveIfNew(batch: TelemetryBatch): boolean {
    if (this.batches.has(batch.batchId)) return false;
    this.batches.set(batch.batchId, batch);
    return true;
  }
}

export class InMemorySosEventRepository implements SosEventRepository {
  private readonly events = new Map<string, SosTrigger>();
  private readonly cancelled = new Set<string>();

  saveIfNew(event: SosTrigger): boolean {
    if (this.events.has(event.eventId)) return false;
    this.events.set(event.eventId, event);
    return true;
  }

  cancel(event: SosCancel): boolean {
    if (this.cancelled.has(event.eventId)) return false;
    this.cancelled.add(event.eventId);
    return true;
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

export const telemetryRepository = new InMemoryTelemetryRepository();
export const sosEventRepository = new InMemorySosEventRepository();
export const pushNotifier = new LoggingPushNotifier();

// The support-network repository is not available in this delivery yet.
export const acceptedCaregiversFor = (_userId: string): string[] => [];
