import { api } from '@/services/request';
import { type Card } from '@/pages/characters/types';

export async function getCharacter(id?: string) {
  return (await api.get<Card>('/characters/' + id)).data;
}
