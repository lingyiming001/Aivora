import axios from 'axios';
import { useAuth } from '@/store/user';
import { type Tokens } from '@/types/user';
import { portal } from '@/utils/storage';

const refreshInFlight: Record<string, Promise<string> | null> = { user: null, admin: null };
export async function accessToken() {
  const scope = portal();
  const tokens = scope === 'admin' ? useAuth.getState().adminTokens : useAuth.getState().tokens;
  if (!tokens) return null;
  if (tokens.expiresAt !== undefined && tokens.expiresAt <= Date.now()) {
    useAuth.getState().setTokens(null, scope);
    return null;
  }
  try {
    const payload = JSON.parse(atob(tokens.accessToken.split('.')[1])) as { exp: number };
    if (payload.exp > Date.now() / 1000 + 30) return tokens.accessToken;
  } catch {
    /* Refresh invalid access tokens. */
  }
  if (!refreshInFlight[scope])
    refreshInFlight[scope] = axios
      .post<Tokens>('/api/auth/refresh', { refreshToken: tokens.refreshToken }, { timeout: 15000 })
      .then(({ data }) => {
        const current =
          scope === 'admin' ? useAuth.getState().adminTokens : useAuth.getState().tokens;
        if (current?.refreshToken !== tokens.refreshToken) throw new Error('会话已结束');
        useAuth.getState().setTokens(data, scope, true);
        return data.accessToken;
      })
      .catch((error) => {
        if (axios.isAxiosError(error) && [401, 403].includes(error.response?.status ?? 0)) {
          const current =
            scope === 'admin' ? useAuth.getState().adminTokens : useAuth.getState().tokens;
          if (current?.refreshToken === tokens.refreshToken)
            useAuth.getState().setTokens(null, scope);
        }
        throw error;
      })
      .finally(() => {
        refreshInFlight[scope] = null;
      });
  return refreshInFlight[scope];
}
