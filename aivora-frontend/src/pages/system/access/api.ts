import { api } from '@/services/request';
import { type Role, type MenuPermission } from './types';

export async function getRoles() {
  return (await api.get<Role[]>('/admin/roles')).data;
}
export async function getPermissions() {
  return (await api.get<Record<string, string>>('/admin/permissions')).data;
}

export async function getPermissionCatalog() {
  return (await api.get<MenuPermission[]>('/admin/permissions/catalog')).data;
}
