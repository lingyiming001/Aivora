import { api } from '@/services/request';

export function startCharacterChat(characterId: number) {
  return api.post<{ id: number }>('/chats', { characterId });
}
