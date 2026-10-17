import { useQuery } from '@tanstack/react-query';
import { getProfile } from './api';
import { useAuth } from '@/hooks/useAuth';

export function useProfile() {
  const token = useAuth((s) => s.tokens);
  return useQuery({
    queryKey: ['profile', token?.accessToken],
    enabled: !!token,
    queryFn: getProfile,
  });
}
