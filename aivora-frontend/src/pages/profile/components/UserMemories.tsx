import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Select, Switch } from 'antd';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import { explain } from '@/utils/actionError';

const labels: Record<string, string> = {
  likes: '喜欢的事物',
  dislikes: '不喜欢的事物',
  hobbies: '爱好',
  reply_style: '回复偏好',
};
type Memory = { memory_key: string; content: string };
export default function UserMemories() {
  const token = useAuth((s) => s.tokens);
  const client = useQueryClient();
  const queryKey = ['user-memories', token?.accessToken];
  const q = useQuery({
    queryKey,
    enabled: !!token,
    queryFn: async () =>
      (await api.get<{ enabled: boolean; items: Memory[] }>('/user/memories')).data,
  });
  const [key, K] = useState('likes'),
    [content, C] = useState('');
  const mutation = useMutation({
    mutationFn: async (action: {
      type: 'save' | 'delete' | 'setting';
      key?: string;
      enabled?: boolean;
    }) => {
      if (action.type === 'save')
        await api.put('/user/memories/' + key, { content: content.trim() });
      else if (action.type === 'delete') await api.delete('/user/memories/' + action.key);
      else await api.put('/user/memories', { enabled: action.enabled });
    },
    onSuccess: async (_, action) => {
      if (action.type === 'save') C('');
      await client.invalidateQueries({ queryKey });
    },
  });
  return (
    <section className="panel" style={{ marginTop: 24 }}>
      <h2>长期记忆</h2>
      <p>
        记住明确表达的喜好、爱好和回复偏好，在不同角色聊天中参考。每类保留最新一条，可随时修改或删除。
      </p>
      <p>
        例如：“我喜欢科幻”“我的爱好是摄影”“我希望你以后回答简短”。请勿填写敏感资料；角色扮演中请避免用这些句式描述虚构偏好。
      </p>
      {q.isPending ? (
        <p>正在读取记忆…</p>
      ) : q.isError ? (
        <p role="alert">
          读取失败 <Button onClick={() => void q.refetch()}>重试</Button>
        </p>
      ) : (
        <>
          <label>
            自动记录并在聊天中使用{' '}
            <Switch
              checked={q.data.enabled}
              disabled={mutation.isPending}
              onChange={(enabled) => mutation.mutate({ type: 'setting', enabled })}
            />
          </label>
          <p>
            关闭后保留已有记忆，但不自动记录或用于聊天。删除后若再次明确表达同类偏好，将重新记录。
          </p>
          {q.data.items.length === 0 && <p>还没有记录偏好。</p>}
          {q.data.items.map((item) => (
            <div
              key={item.memory_key}
              style={{
                margin: '12px 0',
                display: 'flex',
                gap: 12,
                alignItems: 'center',
                flexWrap: 'wrap',
              }}
            >
              <strong>{labels[item.memory_key]}</strong>
              <span style={{ overflowWrap: 'anywhere', flex: 1 }}>{item.content}</span>
              <Button
                disabled={mutation.isPending}
                onClick={() => {
                  K(item.memory_key);
                  C(item.content);
                }}
              >
                修改
              </Button>
              <Button
                disabled={mutation.isPending}
                onClick={() => mutation.mutate({ type: 'delete', key: item.memory_key })}
              >
                删除
              </Button>
            </div>
          ))}
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 16 }}>
            <Select
              aria-label="偏好类型"
              value={key}
              style={{ minWidth: 140 }}
              disabled={mutation.isPending}
              onChange={(value) => {
                K(value);
                C(q.data.items.find((item) => item.memory_key === value)?.content ?? '');
              }}
              options={Object.entries(labels).map(([value, label]) => ({ value, label }))}
            />
            <Input
              aria-label="偏好内容"
              placeholder="填写你希望被记住的偏好"
              maxLength={300}
              value={content}
              disabled={mutation.isPending}
              style={{ flex: 1, minWidth: 180 }}
              onChange={(e) => C(e.target.value)}
            />
            <Button
              type="primary"
              disabled={!content.trim() || mutation.isPending}
              onClick={() => mutation.mutate({ type: 'save' })}
            >
              保存偏好
            </Button>
          </div>
        </>
      )}
      {mutation.isError && (
        <p className="auth-error" role="alert">
          {explain(mutation.error)}
        </p>
      )}
    </section>
  );
}
