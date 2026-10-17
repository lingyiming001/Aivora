export type Row = {
  id: string;
  request_key: string;
  nickname?: string;
  status: string;
  stars: number;
  amount_cents: number;
  delta: number;
  cost_stars: number;
  [key: string]: unknown;
};
