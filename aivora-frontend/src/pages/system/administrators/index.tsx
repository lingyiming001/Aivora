import CreateAdmin from './CreateAdmin';
import { useConfirm } from '@/components/ConfirmDialog';
import AdminSummary from '@/components/AdminSummary/index';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag, Modal, Input, Select } from 'antd';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { msg } from '@/pages/system/access/helpers';
import { type Admin } from '@/pages/system/administrators/types';
import { useRoles } from '@/pages/system/access/hooks';

export function AdministratorAdmin() {
  const confirm = useConfirm();
  const q = useQuery({
    queryKey: ['admin', 'access'],
    queryFn: async () => (await api.get<{ admins: Admin[] }>('/admin/access')).data,
  });
  const roles = useRoles();
  const users = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () =>
      (
        await api.get<
          Array<{ id: number; nickname: string; email: string; role: string; status: string }>
        >('/admin/users')
      ).data,
  });
  const [creating, CREATE] = useState(false);
  const [selected, S] = useState<Admin | null>(null),
    [ids, I] = useState<number[]>([]),
    [user, U] = useState<number>(),
    [query, Q] = useState(''),
    [error, E] = useState(''),
    [busy, B] = useState(false);
  async function action(path: string, body?: unknown) {
    B(true);
    E('');
    try {
      if (body) await api.put(path, body);
      else await api.post(path);
      S(null);
      await q.refetch();
      await users.refetch();
      await qc.invalidateQueries({ queryKey: ['admin-session'] });
    } catch (e) {
      E(msg(e));
    } finally {
      B(false);
    }
  }
  return (
    <main className="admin-main">
      <p className="eyebrow">SYSTEM / ADMINISTRATORS</p>
      <h1>管理员管理</h1>
      <p className="muted">
        管理员关联一个或多个业务角色，权限取启用角色的并集；超级管理员保留系统管理权限。
      </p>
      {error && <p role="alert">{error}</p>}
      <AdminSummary
        items={[
          { label: '管理员总数', value: q.data?.admins.length, note: '独立后台身份与会话' },
          {
            label: '启用管理员',
            value: q.data?.admins.filter((a) => a.status === 'ACTIVE').length,
            note: '当前允许登录的账号',
          },
          {
            label: '可分配角色',
            value: roles.data?.filter((r) => r.enabled).length,
            note: '启用角色的权限取并集',
          },
        ]}
      />
      <div className="section-heading">
        <Input.Search
          aria-label="搜索管理员"
          placeholder="搜索昵称或邮箱"
          style={{ maxWidth: 300 }}
          value={query}
          onChange={(e) => Q(e.target.value)}
        />
        <div className="actions">
          <Button type="primary" onClick={() => CREATE(true)}>
            新增管理员
          </Button>
          <Select
            aria-label="选择用户添加管理员"
            style={{ minWidth: 240 }}
            placeholder="选择启用的用户"
            value={user}
            onChange={U}
            options={users.data
              ?.filter((u) => u.role === 'USER' && u.status === 'ACTIVE')
              .map((u) => ({ value: u.id, label: u.nickname + ' · ' + u.email }))}
          />
          <Button
            disabled={!user || busy}
            onClick={async () => {
              if (
                await confirm({
                  title: '添加管理员？',
                  description: '所选用户将获得后台登录资格。添加后请为其分配权限角色。',
                  note: '该用户需要重新登录后台。',
                  confirmLabel: '设为管理员',
                })
              )
                void action('/admin/access/' + user + '/promote');
            }}
          >
            添加管理员
          </Button>
        </div>
      </div>
      <Table
        rowKey="id"
        scroll={{ x: 1100 }}
        loading={q.isPending}
        dataSource={q.data?.admins.filter((a) => (a.nickname + ' ' + a.email).includes(query))}
        columns={[
          { title: '管理员', dataIndex: 'nickname' },
          { title: '账号', dataIndex: 'email' },
          { title: '绑定手机', dataIndex: 'phone', render: (v) => v || '未绑定' },
          { title: '有效会话', dataIndex: 'session_count' },
          {
            title: '关联角色',
            render: (_, a: Admin) =>
              a.admin_super ? (
                <Tag color="green">超级管理员</Tag>
              ) : a.role_ids.length ? (
                a.role_ids.map((id) => (
                  <Tag key={id}>{roles.data?.find((r) => r.id === id)?.name || id}</Tag>
                ))
              ) : (
                <Tag>未分配角色</Tag>
              ),
          },
          { title: '状态', render: (_, a: Admin) => (a.status === 'ACTIVE' ? '启用' : '停用') },
          {
            title: '操作',
            render: (_, a: Admin) =>
              !a.admin_super && (
                <div className="actions">
                  <Button
                    onClick={() => {
                      S(a);
                      I(a.role_ids);
                      E('');
                    }}
                  >
                    分配角色
                  </Button>
                  <Button
                    danger={a.status === 'ACTIVE'}
                    disabled={busy}
                    onClick={async () => {
                      if (
                        await confirm({
                          title: a.status === 'ACTIVE' ? '停用管理员账号？' : '启用管理员账号？',
                          description:
                            a.status === 'ACTIVE'
                              ? `“${a.nickname}”将无法登录，现有会话会失效。`
                              : `“${a.nickname}”可以重新登录后台。`,
                          confirmLabel: a.status === 'ACTIVE' ? '停用账号' : '启用账号',
                          danger: a.status === 'ACTIVE',
                        })
                      )
                        void action('/admin/access/' + a.id + '/status', {
                          status: a.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                        });
                    }}
                  >
                    {a.status === 'ACTIVE' ? '停用' : '启用'}
                  </Button>
                  <Button
                    disabled={busy}
                    onClick={async () => {
                      if (
                        await confirm({
                          title: '强制下线？',
                          description: `将撤销“${a.nickname}”的全部登录会话，对方可重新登录。`,
                          confirmLabel: '强制下线',
                        })
                      )
                        void action('/admin/access/' + a.id + '/logout');
                    }}
                  >
                    强制下线
                  </Button>
                  <Button
                    danger
                    disabled={busy}
                    onClick={async () => {
                      if (
                        await confirm({
                          title: '移除管理员身份？',
                          description: `“${a.nickname}”将恢复为普通用户，无法继续使用管理后台。`,
                          note: '此操作会移除该用户的后台管理资格。',
                          confirmLabel: '移除身份',
                          danger: true,
                        })
                      )
                        void action('/admin/access/' + a.id + '/demote');
                    }}
                  >
                    移除管理员
                  </Button>
                </div>
              ),
          },
        ]}
      />
      {creating && (
        <CreateAdmin
          roles={roles.data ?? []}
          close={() => CREATE(false)}
          saved={() => {
            void q.refetch();
            void users.refetch();
          }}
        />
      )}
      <Modal
        centered
        title={'分配角色 · ' + selected?.nickname}
        open={!!selected}
        onCancel={() => !busy && S(null)}
        onOk={() => action('/admin/access/' + selected?.id + '/roles', { roleIds: ids })}
        confirmLoading={busy}
      >
        <p>不直接给个人勾选菜单，由角色统一维护权限。</p>
        <Select
          mode="multiple"
          aria-label="管理员角色"
          style={{ width: '100%' }}
          value={ids}
          onChange={I}
          options={roles.data
            ?.filter((r) => r.enabled)
            .map((r) => ({ value: r.id, label: r.name }))}
        />
        {error && <p role="alert">{error}</p>}
      </Modal>
    </main>
  );
}
