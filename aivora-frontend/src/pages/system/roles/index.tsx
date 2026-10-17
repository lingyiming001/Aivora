import { useConfirm } from '@/components/ConfirmDialog';
import AdminSummary from '@/components/AdminSummary/index';
import { useState } from 'react';
import { Button, Table, Tag, Input } from 'antd';
import { api } from '@/services/request';
import { msg } from '@/pages/system/access/helpers';
import { type Role } from '@/pages/system/access/types';
import { useRoles, usePermissions } from '@/pages/system/access/hooks';
import { RoleForm } from '@/pages/system/roles/components/RoleForm';

export function RolesAdmin() {
  const confirm = useConfirm();
  const q = useRoles(),
    perms = usePermissions();
  const [edit, E] = useState<Role | null | undefined>(),
    [query, Q] = useState(''),
    [error, X] = useState('');
  const [removing, setRemoving] = useState<number | null>(null);
  async function remove(id: number) {
    if (removing !== null) return;
    if (
      !(await confirm({
        title: '删除权限角色？',
        description: `“${q.data?.find((role) => role.id === id)?.name || '此角色'}”将被永久删除。仅未分配给管理员的角色可以删除。`,
        note: '此操作无法撤销，请确认后继续。',
        confirmLabel: '确认删除',
        danger: true,
      }))
    )
      return;
    setRemoving(id);
    try {
      await api.delete('/admin/roles/' + id);
      await q.refetch();
    } catch (e) {
      X(msg(e));
    } finally {
      setRemoving(null);
    }
  }
  return (
    <main className="admin-main">
      <p className="eyebrow">SYSTEM / ROLES</p>
      <h1>角色管理</h1>
      <p className="muted">这里管理后台权限角色；AI 对话角色位于“角色卡管理”。</p>
      <AdminSummary
        items={[
          { label: '权限角色', value: q.data?.length, note: '按岗位集中维护授权' },
          {
            label: '启用角色',
            value: q.data?.filter((r) => r.enabled).length,
            note: '授权即时参与权限校验',
          },
          {
            label: '已关联成员',
            value: q.data?.reduce((sum, r) => sum + r.member_count, 0),
            note: '同一管理员可关联多个角色',
          },
        ]}
      />
      <div className="section-heading">
        <Input.Search
          aria-label="搜索权限角色"
          placeholder="搜索角色名称或编码"
          value={query}
          style={{ maxWidth: 300 }}
          onChange={(e) => Q(e.target.value)}
        />
        <Button type="primary" onClick={() => E(null)}>
          新增角色
        </Button>
      </div>
      {error && <p role="alert">{error}</p>}
      <Table
        rowKey="id"
        loading={q.isPending}
        dataSource={q.data?.filter((r) =>
          (r.name + ' ' + r.code).toLowerCase().includes(query.toLowerCase()),
        )}
        columns={[
          { title: '角色名称', dataIndex: 'name' },
          { title: '角色编码', dataIndex: 'code' },
          { title: '说明', dataIndex: 'description' },
          { title: '成员数', dataIndex: 'member_count' },
          {
            title: '状态',
            render: (_, r: Role) => (
              <Tag color={r.enabled ? 'green' : 'default'}>{r.enabled ? '启用' : '停用'}</Tag>
            ),
          },
          {
            title: '操作',
            render: (_, r: Role) => (
              <div className="actions">
                <Button onClick={() => E(r)}>编辑与授权</Button>
                <Button
                  danger
                  loading={removing === r.id}
                  disabled={r.member_count > 0 || removing !== null}
                  onClick={() => remove(r.id)}
                >
                  删除
                </Button>
              </div>
            ),
          },
        ]}
      />
      {edit !== undefined && (
        <RoleForm
          key={edit?.id ?? 'new'}
          role={edit ?? undefined}
          permissions={perms.data ?? {}}
          close={() => E(undefined)}
          saved={() => q.refetch()}
        />
      )}
    </main>
  );
}
