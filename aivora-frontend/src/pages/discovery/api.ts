import { api } from '@/services/request';
import { type Card } from '@/pages/characters/types';

export async function getCharacters(q: string, category: string) {
  return (await api.get<Card[]>('/characters', { params: { q, category } })).data;
}
