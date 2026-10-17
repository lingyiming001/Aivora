import { useQuery } from '@tanstack/react-query';
import { getRoles, getPermissions, getPermissionCatalog } from './api';

export function useRoles() {
  return useQuery({
    queryKey: ['admin', 'roles'],
    queryFn: getRoles,
  });
}

export function usePermissions() {
  return useQuery({
    queryKey: ['admin', 'permissions'],
    queryFn: getPermissions,
  });
}

export function usePermissionCatalog() {
  return useQuery({ queryKey: ['admin', 'permission-catalog'], queryFn: getPermissionCatalog });
}
