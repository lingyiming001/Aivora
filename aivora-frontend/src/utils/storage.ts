import { type Tokens } from '@/types/user';
import { SESSION_MAX_AGE as WEEK } from '@/config/env';

export const portal = () => (location.pathname.startsWith('/admin') ? 'admin' : 'user');
export const storageKey = (scope: string) => 'aivora_' + scope + '_tokens';
export function readTokens(scope: string): Tokens | null {
  const key = storageKey(scope);
  for (const storage of [sessionStorage, localStorage]) {
    try {
      const raw = storage.getItem(key);
      if (!raw) continue;
      const value = JSON.parse(raw) as Tokens;
      if (typeof value.accessToken !== 'string' || typeof value.refreshToken !== 'string')
        throw new Error();
      if (
        value.expiresAt !== undefined &&
        (!Number.isFinite(value.expiresAt) || value.expiresAt <= Date.now())
      ) {
        storage.removeItem(key);
        continue;
      }
      if (value.expiresAt === undefined) {
        value.expiresAt = Date.now() + WEEK;
        storage.setItem(key, JSON.stringify(value));
      }
      return value;
    } catch {
      storage.removeItem(key);
    }
  }
  return null;
}
