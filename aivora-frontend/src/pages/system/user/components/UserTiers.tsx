import AdminSummary from '@/components/AdminSummary/index';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Table } from 'antd';
import { api } from '@/services/request';
import { tiers } from '@/constants/membership';
import { errorMessage } from '@/pages/system/user/helpers';

export function UserTiers() {
  const users = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () =>
      (
        await api.get<
          Array<{
            id: number;
            nickname: string;
            email: string;
            role: string;
            status: string;
            balance: number;
            membership_tier: string;
          }>
        >('/admin/users')
      ).data,
  });
  const [busy, B] = useState(false),
    [error, E] = useState('');
  async function save(id: number, tier: string) {
    B(true);
    E('');
    try {
      await api.put('/admin/users/' + id + '/tier', { tier });
      await users.refetch();
    } catch (e) {
      E(errorMessage(e));
    } finally {
      B(false);
    }
  }
  return (
    <>
      <AdminSummary
        items={[
          { label: '用户总数', value: users.data?.length, note: '平台账号与会员等级' },
          {
            label: '启用账号',
            value: users.data?.filter((u) => u.status === 'ACTIVE').length,
            note: '可正常登录和使用服务',
          },
          {
            label: 'VIP / SVIP',
            value: users.data?.filter((u) => ['VIP', 'SVIP'].includes(u.membership_tier)).length,
            note: '根据等级匹配模型路由',
          },
        ]}
      />
      <p className="helper muted">
        用户等级决定模型路由。此处为管理员等级设置，不会自动触发支付或修改星币余额。
      </p>
      {error && <p role="alert">{error}</p>}
      <Table
        rowKey="id"
        loading={users.isPending}
        dataSource={users.data}
        scroll={{ x: 800 }}
        columns={[
          { title: '昵称', dataIndex: 'nickname' },
          { title: '邮箱', dataIndex: 'email' },
          { title: '身份', dataIndex: 'role' },
          {
            title: '等级',
            render: (_, u) => (
              <select
                aria-label={u.nickname + '的等级'}
                disabled={busy}
                value={u.membership_tier}
                onChange={(e) => save(u.id, e.target.value)}
              >
                {Object.entries(tiers)
                  .filter(([v]) => v !== 'GUEST')
                  .map(([v, n]) => (
                    <option key={v} value={v}>
                      {n}
                    </option>
                  ))}
              </select>
            ),
          },
          {
            title: '状态',
            render: (_, u) =>
              u.role === 'USER' ? (
                <select
                  aria-label={u.nickname + '的状态'}
                  disabled={busy}
                  value={u.status}
                  onChange={async (e) => {
                    B(true);
                    E('');
                    try {
                      await api.put('/admin/users/' + u.id + '/status', { status: e.target.value });
                      await users.refetch();
                    } catch (err) {
                      E(errorMessage(err));
                    } finally {
                      B(false);
                    }
                  }}
                >
                  <option value="ACTIVE">启用</option>
                  <option value="DISABLED">停用</option>
                </select>
              ) : (
                u.status
              ),
          },
          { title: '星币', dataIndex: 'balance' },
        ]}
      />
    </>
  );
}
