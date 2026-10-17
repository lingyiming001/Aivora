import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Bookmark, ThumbsUp, ThumbsDown } from 'lucide-react';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { useAuth } from '@/hooks/useAuth';
import axios from 'axios';
import { type Engagement } from '@/components/CharacterActions/types';

export function useEngagements() {
  const token = useAuth((s) => s.tokens);
  return useQuery({
    queryKey: ['engagements', !!token],
    queryFn: async () => (await api.get<Engagement[]>('/characters/engagements')).data,
  });
}

export default function CharacterActions({ id }: { id: number }) {
  const q = useEngagements(),
    token = useAuth((s) => s.tokens),
    nav = useNavigate(),
    [busy, B] = useState(false),
    [error, E] = useState('');
  const v = q.data?.find((e) => e.character_id === id);
  async function change(payload: { favorite?: boolean; vote?: number }) {
    if (!token) {
      nav('/login');
      return;
    }
    if (busy) return;
    B(true);
    E('');
    try {
      await api.put('/characters/' + id + '/engagement', payload);
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['engagements'] }),
        qc.invalidateQueries({ queryKey: ['favorites'] }),
      ]);
    } catch (e) {
      E(
        axios.isAxiosError(e)
          ? e.response?.data?.message || '操作失败，请重试'
          : '操作失败，请重试',
      );
    } finally {
      B(false);
    }
  }
  return (
    <div className="character-social" onClick={(e) => e.stopPropagation()}>
      <div>
        {[
          {
            key: 'favorite',
            label: '收藏',
            Icon: Bookmark,
            active: !!v?.favorite,
            count: v?.favorites || 0,
            act: () => change({ favorite: !v?.favorite }),
          },
          {
            key: 'like',
            label: '点赞',
            Icon: ThumbsUp,
            active: v?.vote === 1,
            count: v?.likes || 0,
            act: () => change({ vote: v?.vote === 1 ? 0 : 1 }),
          },
          {
            key: 'dislike',
            label: '点踩',
            Icon: ThumbsDown,
            active: v?.vote === -1,
            count: v?.dislikes || 0,
            act: () => change({ vote: v?.vote === -1 ? 0 : -1 }),
          },
        ].map(({ key, label, Icon, active, count, act }) => (
          <button
            key={key}
            type="button"
            aria-label={label + '角色'}
            aria-pressed={active}
            disabled={busy || q.isPending || q.isError}
            onClick={() => void act()}
          >
            <Icon size={14} fill={active ? 'currentColor' : 'none'} />
            <span>{Number(count) || label}</span>
          </button>
        ))}
      </div>
      {(error || q.isError) && (
        <small role="alert">
          {error || '评价加载失败'}
          {q.isError && <button onClick={() => q.refetch()}>重试</button>}
        </small>
      )}
    </div>
  );
}
