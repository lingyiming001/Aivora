export type Group = {
  day: string;
  character_id: number;
  character_name: string;
  messages: number;
  spent: number;
  refunded: number;
  net: number;
  sessions: number;
};

export type Entry = {
  id: number;
  delta: number;
  balance_after: number;
  reason: string;
  created_at: string;
  session_id?: number;
  request_status?: string;
  total: number;
};
