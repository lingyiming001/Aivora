import { reason } from '@/pages/wallet/index';

export type Package = { id: number; name: string; amount_cents: number; stars: number };

export type Ledger = {
  character_name?: string;
  id: number;
  delta: number;
  balance_after: number;
  reason: string;
  created_at: string;
};

export type Order = {
  id: string;
  package_id: number;
  amount_cents: number;
  stars: number;
  status: string;
  created_at: string;
};
