import { api } from '@/services/request';
import { type Favorite } from './types';

export async function getFavorites() {
  return (await api.get<Favorite[]>('/characters/favorites')).data;
}
