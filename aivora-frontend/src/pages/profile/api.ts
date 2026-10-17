import { api } from '@/services/request';
import { type Profile, type ProfileUpdate } from './types';

export async function getProfile() {
  return (await api.get<Profile>('/profile')).data;
}
export function updateProfile(request: ProfileUpdate) {
  return api.put('/profile', request);
}
