import { useState } from 'react';
import { Button, Input, InputNumber, Modal, Table, Tag } from 'antd';
import { Plus, ShieldCheck } from 'lucide-react';
import AdminSummary from '@/components/AdminSummary';
import { useConfirm } from '@/components/ConfirmDialog';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { usePermissionCatalog } from '../access/hooks';
import { type MenuPermission } from '../access/types';
import { msg } from '../access/helpers';

export function PermissionsAdmin() {
  const q = usePermissionCatalog(),
    confirm = useConfirm();
  const [editing, E] = useState<MenuPermission | null | undefined>(),
    [search, S] = useState(''),
    [error, ERR] = useState(''),
    [deleting, D] = useState<string | null>(null);
  async function refresh() {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['admin', 'permission-catalog'] }),
      qc.invalidateQueries({ queryKey: ['admin', 'permissions'] }),
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] }),
      qc.invalidateQueries({ queryKey: ['admin-session'] }),
    ]);
  }
  async function remove(row: MenuPermission) {
    if (
      deleting ||
      !(await confirm({
        title: '删除菜单权限？',
        description: `“${row.name}”将从后台菜单移除，关联角色的此项授权也会撤销。`,
        note: '对应业务数据会保留。',
        confirmLabel: '删除菜单',
        danger: true,
      }))
    )
      return;
    D(row.menu_key);
    ERR('');
    try {
      await api.delete('/admin/permissions/' + row.menu_key);
      await refresh();
    } catch (e) {
      ERR(msg(e));
    } finally {
      D(null);
    }
  }
  return (
    <main className="admin-main">
      <p className="eyebrow">SYSTEM / PERMISSIONS</p>
      <div className="section-heading">
        <div>
          <h1>菜单权限</h1>
          <p className="muted">
            维护菜单目录与分组，通过角色配置访问权限。系统授权管理菜单保留给超级管理员。
          </p>
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={() => E(null)}>
          新增菜单
        </Button>
      </div>
      <AdminSummary
        items={[
          { label: '注册菜单', value: q.data?.length, note: '后台导航与角色授权同步' },
          {
            label: '业务分组',
            value: q.data ? new Set(q.data.map((x) => x.group_name)).size : undefined,
            note: '按业务职责组织菜单',
          },
          {
            label: '系统专属菜单',
            value: q.data?.filter((x) => x.system_only).length,
            note: '保护授权管理入口',
          },
        ]}
      />
      <Input.Search
        aria-label="搜索菜单"
        placeholder="搜索名称或权限标识"
        value={search}
        onChange={(e) => S(e.target.value)}
        style={{ maxWidth: 320, marginBottom: 20 }}
      />
      {(error || q.isError) && (
        <p role="alert" className="auth-error">
          {error || '菜单目录加载失败'}
          {q.isError && <Button onClick={() => q.refetch()}>重试</Button>}
        </p>
      )}
      <Table
        rowKey="menu_key"
        loading={q.isPending}
        scroll={{ x: 950 }}
        dataSource={q.data?.filter((r) =>
          (r.name + ' ' + r.menu_key).toLowerCase().includes(search.toLowerCase()),
        )}
        columns={[
          { title: '菜单名称', dataIndex: 'name' },
          { title: '权限标识', dataIndex: 'menu_key' },
          { title: '业务分组', dataIndex: 'group_name' },
          { title: '页面路径', dataIndex: 'path' },
          { title: '顺序', dataIndex: 'sort_order' },
          {
            title: '授权范围',
            render: (_, r) => <Tag>{r.system_only ? '仅超级管理员' : '业务角色授权'}</Tag>,
          },
          {
            title: '操作',
            render: (_, r) => (
              <div className="actions">
                <Button disabled={!!deleting} onClick={() => E(r)}>
                  修改
                </Button>
                <Button
                  danger
                  disabled={r.system_only || !!deleting}
                  loading={deleting === r.menu_key}
                  onClick={() => void remove(r)}
                >
                  删除
                </Button>
              </div>
            ),
          },
        ]}
      />
      {editing !== undefined && (
        <MenuForm
          key={editing?.menu_key ?? 'new'}
          row={editing ?? undefined}
          close={() => E(undefined)}
          saved={refresh}
        />
      )}
    </main>
  );
}
function MenuForm({
  row,
  close,
  saved,
}: {
  row?: MenuPermission;
  close: () => void;
  saved: () => Promise<void>;
}) {
  const [key, K] = useState(row?.menu_key ?? ''),
    [name, N] = useState(row?.name ?? ''),
    [group, G] = useState(row?.group_name ?? '平台运营'),
    [description, D] = useState(row?.description ?? ''),
    [order, O] = useState(row?.sort_order ?? 130),
    [busy, B] = useState(false),
    [error, E] = useState('');
  async function save() {
    if (busy) return;
    B(true);
    E('');
    try {
      const body = { key, name, groupName: group, description, sortOrder: order };
      if (row) await api.put('/admin/permissions/' + row.menu_key, body);
      else await api.post('/admin/permissions', body);
      await saved();
      close();
    } catch (e) {
      E(msg(e));
    } finally {
      B(false);
    }
  }
  return (
    <Modal
      open
      centered
      width={640}
      className="av-management-dialog"
      footer={null}
      maskClosable={false}
      keyboard={!busy}
      closable={!busy}
      onCancel={() => !busy && close()}
      title={
        <div className="management-modal-title">
          <ShieldCheck size={22} />
          <div>
            <h2>{row ? '修改菜单权限' : '新增菜单权限'}</h2>
            <p>配置导航名称、分组与授权标识</p>
          </div>
        </div>
      }
    >
      <form
        className="management-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <div className="management-form-body management-field-grid">
          <label>
            菜单名称
            <Input
              required
              maxLength={80}
              value={name}
              disabled={busy}
              onChange={(e) => N(e.target.value)}
            />
          </label>
          <label>
            权限标识
            <Input
              required
              maxLength={40}
              pattern="[a-z][a-z0-9-]{1,39}"
              value={key}
              disabled={busy || !!row}
              placeholder="例如：reports"
              onChange={(e) => K(e.target.value)}
            />
            <small>小写字母、数字与连字符；创建后不可修改。</small>
          </label>
          <label>
            业务分组
            <Input
              required
              maxLength={40}
              value={group}
              disabled={busy}
              onChange={(e) => G(e.target.value)}
            />
          </label>
          <label>
            排序
            <InputNumber
              min={0}
              precision={0}
              value={order}
              disabled={busy}
              onChange={(v) => O(v ?? 0)}
              style={{ width: '100%' }}
            />
          </label>
          <label className="management-field-wide">
            页面路径
            <Input value={key === 'overview' ? '/admin' : '/admin/' + key} readOnly />
          </label>
          <label className="management-field-wide">
            说明
            <Input.TextArea
              maxLength={300}
              rows={3}
              value={description}
              disabled={busy}
              onChange={(e) => D(e.target.value)}
            />
          </label>
          {!row && (
            <p className="muted management-field-wide">
              新增菜单会注册导航与角色授权。业务页面和接口需另行接入，注册后显示待接入页面。
            </p>
          )}
          {error && (
            <p role="alert" className="auth-error management-field-wide">
              {error}
            </p>
          )}
        </div>
        <div className="management-form-footer">
          <span className="management-footer-spacer" />
          <Button disabled={busy} onClick={close}>
            取消
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={busy}
            disabled={!key.trim() || !name.trim() || !group.trim()}
          >
            保存菜单
          </Button>
        </div>
      </form>
    </Modal>
  );
}
