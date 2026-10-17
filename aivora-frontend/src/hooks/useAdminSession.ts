import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/hooks/useAuth';
import { api } from '@/services/request';
import { type AdminSession } from '@/types/user';

export function useAdminSession(enabled = true) {
  const tokens = useAuth((state) => state.adminTokens);
  return useQuery({
    // The fixed login deadline survives access-token refresh; logout/login clears the cache.
    queryKey: ['admin-session', tokens?.expiresAt ?? tokens?.refreshToken],
    enabled: enabled && !!tokens,
    staleTime: 15000,
    refetchInterval: 30000,
    retry: false,
    queryFn: async () => (await api.get<AdminSession>('/auth/me')).data,
  });
}
