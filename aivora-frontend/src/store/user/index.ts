import { create } from 'zustand';
import { type Tokens } from '@/types/user';
import { qc } from '@/services/queryClient';
import { portal, storageKey, readTokens } from '@/utils/storage';
import { SESSION_MAX_AGE as WEEK } from '@/config/env';

export const useAuth = create<{
  tokens: Tokens | null;
  adminTokens: Tokens | null;
  setTokens: (
    v: Tokens | null,
    scope?: string,
    preserveCache?: boolean,
    remember?: boolean,
  ) => void;
}>((set) => ({
  tokens: readTokens('user'),
  adminTokens: readTokens('admin'),
  setTokens: (v, scope = portal(), preserveCache = false, remember) => {
    const key = storageKey(scope),
      persistent = remember ?? (preserveCache ? localStorage.getItem(key) !== null : true);
    const current = scope === 'admin' ? useAuth.getState().adminTokens : useAuth.getState().tokens;
    const next = v
      ? {
          ...v,
          expiresAt: Math.min(
            v.expiresAt ?? Date.now() + WEEK,
            preserveCache ? (current?.expiresAt ?? Infinity) : Date.now() + WEEK,
          ),
        }
      : null;
    localStorage.removeItem(key);
    sessionStorage.removeItem(key);
    if (next) (persistent ? localStorage : sessionStorage).setItem(key, JSON.stringify(next));
    if (!preserveCache) qc.clear();
    set(scope === 'admin' ? { adminTokens: next } : { tokens: next });
  },
}));
