export type FogKind =
  | 'telemetry'
  | 'sos'
  | 'sos-cancel'
  | 'suspected'
  | 'decision'
  | 'capabilities';

export interface FogEntry {
  kind: FogKind;
  key: string;
  entityId: string;
  envelope: string;
  receivedAt: number;
}

export interface WearRecord {
  id: string;
  capturedAt: string;
  type: string;
  payload: Record<string, unknown> | null;
}

export interface WearTelemetryEnvelope {
  schemaVersion: string;
  targetEndpoint: string;
  transport: string;
  batchId: string;
  startedAt: string;
  endedAt: string;
  mobileEnrichmentRequired: string[];
  records: WearRecord[];
}

export interface WearSosEnvelope {
  schemaVersion: string;
  targetEndpoint: string;
  transport: string;
  eventId: string;
  triggeredAt: string;
  source: string;
  reason?: string;
  state?: string;
  score?: number;
  rulesVersion?: string;
  mobileEnrichmentRequired: string[];
}

export interface WearSosCancelEnvelope extends WearSosEnvelope {
  cancelled: true;
  cancelledAt?: string;
}

export interface SuspectedEventFeaturesPayload {
  heartRateMean: number | null;
  heartRateMax: number | null;
  heartRateSlopeBpmPerMinute: number | null;
  heartRateDeltaFromBaseline: number | null;
  rmssdMillis: number | null;
  sdnnMillis: number | null;
  movementMagnitudeMean: number | null;
  movementVariance: number | null;
  validSampleRatio: number;
  lastSampleAgeSeconds: number;
  sampleCount: number;
}

export interface SuspectedEventBaselinePayload {
  sampleCount: number;
  meanHeartRate: number;
  heartRateM2: number;
  updatedAtEpochMillis: number;
}

export interface WearSuspectedEventEnvelope {
  schemaVersion: string;
  targetEndpoint: string;
  transport: string;
  eventId: string;
  detectedAt: string;
  state: string;
  score: number;
  rulesVersion: string;
  features: SuspectedEventFeaturesPayload | null;
  baseline: SuspectedEventBaselinePayload | null;
  mobileEnrichmentRequired: string[];
}

export interface WearEventDecisionEnvelope {
  schemaVersion: string;
  targetEndpoint: string;
  transport: string;
  eventId: string;
  detectedAt: string;
  respondedAt: string;
  response: string;
  mobileEnrichmentRequired: string[];
}

export type PrimaryUserDecision =
  | 'ACTIVITY_CONFIRMED'
  | 'USER_OK'
  | 'SUPPORT_REQUESTED';

export interface SuspectedEventPayload {
  eventId: string;
  userId: string;
  deviceId: string;
  sessionId: string;
  sequence: number;
  detectedAt: string;
  state: string;
  score: number;
  rulesVersion: string;
  features: SuspectedEventFeaturesPayload | null;
  baseline: SuspectedEventBaselinePayload | null;
}

export interface EventDecisionPayload {
  eventId: string;
  userId: string;
  deviceId: string;
  sessionId: string;
  sequence: number;
  detectedAt: string;
  respondedAt: string;
  response: string;
}

export type SignalQuality = 'good' | 'fair' | 'poor' | 'unknown';
export type WearingState = 'onBody' | 'offBody' | 'unknown';

export interface TelemetrySample {
  timestamp: string;
  heartRateBpm: number | null;
  ibiMs: number[];
  accelerometer: { x: number; y: number; z: number } | null;
  skinTemperatureCelsius: number | null;
  ambientTemperatureCelsius: number | null;
  quality: {
    heartRate: SignalQuality;
    ibi: SignalQuality;
    wearingState: WearingState;
  };
}

export interface TelemetryBatchPayload {
  batchId: string;
  deviceId: string;
  userId: string;
  sessionId: string;
  startedAt: string;
  endedAt: string;
  sequence: number;
  samples: TelemetrySample[];
}

export interface SosTriggerPayload {
  eventId: string;
  userId: string;
  deviceId: string;
  triggeredAt: string;
  source: 'WATCH' | 'MOBILE';
  reason?: string;
}

export interface SosCancelPayload {
  eventId: string;
  userId: string;
  deviceId: string;
  cancelledAt: string;
  reason?: string;
}

export interface FogIdentity {
  userId: string;
  deviceId: string;
  sessionId: string;
  sequence: number;
}

export interface AuthResult {
  token: string;
  expiresAt: string;
  user: {
    id: string;
    fullName?: string;
    email: string;
    planId?: string;
    emailVerified?: boolean;
    avatarUrl?: string | null;
  };
}

export interface DeliveryResult {
  entryKey: string;
  kind: FogKind;
  status: 'accepted' | 'duplicate' | 'failed' | 'unauthorized';
  httpCode?: number;
}
