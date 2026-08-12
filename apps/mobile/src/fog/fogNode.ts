import { NativeEventEmitter, NativeModules } from 'react-native';
import type {
  AuthResult,
  DeliveryResult,
  FogEntry,
  FogIdentity,
} from './types';
import { deliverEntry } from './api';
import { FogEndpoints } from './enricher';

const { WearFog } = NativeModules;
const events = new NativeEventEmitter(WearFog);

function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export interface FogNodeState {
  identity: FogIdentity;
  token: string;
  pending: number;
  lastDelivery: DeliveryResult | null;
  unauthorized: boolean;
}

export type FogStateListener = (state: FogNodeState) => void;

const BASE_URL = FogEndpoints.API_BASE;

/**
 * Orquestador del nodo fog.
 *
 *  - recoge sobres del reloj (WearFog.peek / evento FogInbound)
 *  - los enriquece y envía al API del backend
 *  - confirma al reloj por ACK solo cuando el API aceptó (202) o detectó duplicado (200)
 *  - si falla la red, el sobre queda en la cola nativa y se reintenta
 */
class FogNode {
  private identity: FogIdentity = {
    userId: '',
    deviceId: '',
    sessionId: '',
    sequence: 0,
  };
  private token = '';
  private listeners = new Set<FogStateListener>();
  private busy = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  async start(): Promise<void> {
    await this.loadIdentity();
    await WearFog.announceFogPhone();
    events.addListener('FogInbound', () => {
      void this.flush();
    });
    this.timer = setInterval(() => void this.flush(), 15_000);
    await this.flush();
    this.emit();
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    events.removeAllListeners('FogInbound');
  }

  async loadIdentity(): Promise<void> {
    const raw: string = await WearFog.getIdentity();
    const parsed = JSON.parse(raw) as FogIdentity;
    this.identity = {
      userId: parsed.userId ?? '',
      deviceId: parsed.deviceId || uuid(),
      sessionId: parsed.sessionId || uuid(),
      sequence: parsed.sequence ?? 0,
    };
    if (
      parsed.deviceId !== this.identity.deviceId ||
      parsed.sessionId !== this.identity.sessionId
    ) {
      await this.persistIdentity();
    }
  }

  private async persistIdentity(): Promise<void> {
    await WearFog.setIdentity(this.identity);
  }

  async setAuthenticated(auth: AuthResult): Promise<void> {
    this.token = auth.token;
    this.identity = { ...this.identity, userId: auth.user.id };
    await this.persistIdentity();
    this.emit();
    await this.flush();
  }

  async nextSequence(): Promise<number> {
    const next = await WearFog.nextSequence();
    this.identity.sequence = next;
    return next;
  }

  private async flush(): Promise<void> {
    if (this.busy) return;
    this.busy = true;
    try {
      if (!this.token || !this.identity.userId) {
        const pending = await WearFog.inboundCount();
        this.emit();
        if (pending > 0) {
          this.setUnauthorized();
        }
        return;
      }
      const raw = await WearFog.peek();
      const entries: FogEntry[] = JSON.parse(raw || '[]');
      for (const entry of entries) {
        if (!['telemetry', 'sos', 'sos-cancel'].includes(entry.kind)) {
          await WearFog.complete(entry.key);
          continue;
        }
        const result = await deliverEntry(
          entry,
          this.identity,
          this.token,
          BASE_URL,
        );
        if (result.status === 'accepted' || result.status === 'duplicate') {
          await this.nextSequence();
          await WearFog.markCloudAcked(entry.key);
          if (await this.tryAck(entry)) {
            await WearFog.markWatchAcked(entry.key);
            await WearFog.complete(entry.key);
            this.lastDelivery = result;
          }
        } else if (result.status === 'unauthorized') {
          this.setUnauthorized();
          break;
        } else {
          this.lastDelivery = result;
        }
      }
    } finally {
      this.busy = false;
      this.emit();
    }
  }

  private async tryAck(entry: FogEntry): Promise<boolean> {
    try {
      if (entry.kind === 'telemetry') {
        return (await WearFog.ackTelemetry(entry.key)) === true;
      }
      if (entry.kind === 'sos') {
        return (await WearFog.ackSos(entry.key)) === true;
      }
      if (entry.kind === 'sos-cancel') {
        return (await WearFog.ackSosCancel(entry.key)) === true;
      }
      return false;
    } catch {
      return false;
    }
  }

  private lastDelivery: DeliveryResult | null = null;

  private setUnauthorized(): void {
    if (!this.emitState.unauthorized) {
      this.emitState.unauthorized = true;
    }
  }

  private emitState: FogNodeState = {
    identity: this.identity,
    token: this.token,
    pending: 0,
    lastDelivery: null,
    unauthorized: false,
  };

  private emit(): void {
    this.emitState = {
      identity: this.identity,
      token: this.token,
      pending: 0,
      lastDelivery: this.lastDelivery,
      unauthorized: this.emitState.unauthorized,
    };
    for (const listener of this.listeners) {
      listener(this.emitState);
    }
  }

  subscribe(listener: FogStateListener): () => void {
    this.listeners.add(listener);
    listener(this.emitState);
    return () => this.listeners.delete(listener);
  }

  getPending(): Promise<number> {
    return WearFog.inboundCount();
  }

  getState(): FogNodeState {
    return this.emitState;
  }
}

export const fogNode = new FogNode();
export { BASE_URL };
