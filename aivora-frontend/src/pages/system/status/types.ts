export type Budget = {
  month: string;
  limit: number;
  threshold: number;
  baseline: number;
  enabled: boolean;
  measured: number;
  used: number;
  remaining: number;
  remainingPercent: number;
  calls: number;
  unknownCalls: number;
  low: boolean;
};

export type Notice = { id: number; title: string; body: string; created_at: string; read: boolean };
