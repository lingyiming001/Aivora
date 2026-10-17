import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Table, Button, InputNumber } from 'antd';
import { api } from '@/services/request';
import { errorMessage } from '@/pages/system/user/helpers';

export function RolePrices() {
  const rows = useQuery({
    queryKey: ['admin', 'character-prices'],
    queryFn: async () =>
      (
        await api.get<
          Array<{
            id: number;
            name: string;
            category: string;
            stars_per_message: number;
            visibility: string;
          }>
        >('/admin/characters/all')
      ).data,
  });
  const [values, V] = useState<Record<number, number>>({}),
    [busy, B] = useState<number | null>(null),
    [error, E] = useState('');
  async function save(id: number, stars: number) {
    B(id);
    E('');
    try {
      await api.put('/admin/characters/' + id + '/price', { stars });
      await rows.refetch();
    } catch (e) {
      E(errorMessage(e));
    } finally {
      B(null);
    }
  }
  return (
    <section className="role-pricing">
      <h2>角色价格与消耗</h2>
      <p className="helper muted">
        每条成功回复按角色价格计费，失败退回本次预扣；价格同时参与模型路由，下一次发送即生效。
      </p>
      {error && <p role="alert">{error}</p>}
      <Table
        rowKey="id"
        loading={rows.isPending}
        dataSource={rows.data}
        scroll={{ x: 650 }}
        columns={[
          { title: '角色', dataIndex: 'name' },
          { title: '分类', dataIndex: 'category' },
          {
            title: '可见性',
            dataIndex: 'visibility',
            render: (v) => (v === 'PRIVATE' ? '私密' : '公开'),
          },
          {
            title: '星币 / 条',
            render: (_, r) => (
              <InputNumber
                aria-label={r.name + '的价格'}
                min={1}
                max={1000}
                precision={0}
                value={values[r.id] ?? r.stars_per_message}
                onChange={(v) => V((old) => ({ ...old, [r.id]: Number(v ?? 1) }))}
              />
            ),
          },
          {
            title: '操作',
            render: (_, r) => (
              <Button
                loading={busy === r.id}
                onClick={() => save(r.id, values[r.id] ?? r.stars_per_message)}
              >
                保存价格
              </Button>
            ),
          },
        ]}
      />
    </section>
  );
}
