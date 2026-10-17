import { useId, useState } from 'react';
import { Modal, Input, Switch, Checkbox, Button } from 'antd';
import { ShieldCheck, LockKeyhole, Check, Layers3 } from 'lucide-react';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { msg } from '@/pages/system/access/helpers';
import { type Role } from '@/pages/system/access/types';
import { usePermissionCatalog } from '@/pages/system/access/hooks';

export function RoleForm({
  role: r,
  permissions,
  close,
  saved,
}: {
  role?: Role;
  permissions: Record<string, string>;
  close: () => void;
  saved: () => void;
}) {
  const fieldId = useId();
  const catalog = usePermissionCatalog();
  const groups: Record<string, string[]> = {};
  for (const menu of catalog.data ?? [])
    if (!menu.system_only) (groups[menu.group_name] ??= []).push(menu.menu_key);
  const [name, N] = useState(r?.name ?? ''),
    [code, C] = useState(r?.code ?? ''),
    [description, D] = useState(r?.description ?? ''),
    [enabled, EN] = useState(r?.enabled ?? true),
    [menus, M] = useState(r?.menus ?? []),
    [busy, B] = useState(false),
    [error, E] = useState('');
  const available = Object.values(groups)
    .flat()
    .filter((key) => !['access', 'roles', 'permissions'].includes(key));
  const selected = menus.filter((key) => available.includes(key)).length;
  async function save() {
    if (busy) return;
    B(true);
    E('');
    try {
      const body = {
        name,
        code,
        description,
        enabled,
        menus: menus.filter((key) => available.includes(key)),
      };
      if (r) await api.put('/admin/roles/' + r.id, body);
      else await api.post('/admin/roles', body);
      saved();
      await qc.invalidateQueries({ queryKey: ['admin-session'] });
      close();
    } catch (e) {
      E(msg(e));
    } finally {
      B(false);
    }
  }
  return (
    <Modal
      title={
        <div className="av-role-header">
          <span className="av-role-emblem" aria-hidden="true">
            <ShieldCheck size={24} strokeWidth={1.6} />
          </span>
          <div>
            <h2>{r ? '编辑角色与权限' : '创建权限角色'}</h2>
            <p>为岗位定义职责，精确配置后台访问范围。</p>
          </div>
        </div>
      }
      open
      centered
      width={800}
      footer={null}
      className="av-role-dialog"
      rootClassName="av-role-root"
      maskClosable={false}
      keyboard={!busy}
      closable={!busy}
      onCancel={() => !busy && close()}
    >
      <form
        className="av-role-form"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <div className="av-role-body">
          <section className="av-role-section" aria-labelledby={fieldId + '-basics'}>
            <div className="av-role-section-heading">
              <h3 id={fieldId + '-basics'}>
                <span>01</span>基本信息
              </h3>
              <small>标识与状态</small>
            </div>
            <div className="av-role-fields">
              <div className="av-role-field">
                <label htmlFor={fieldId + '-name'}>
                  角色名称<span>必填</span>
                </label>
                <Input
                  id={fieldId + '-name'}
                  required
                  disabled={busy}
                  value={name}
                  maxLength={80}
                  placeholder="例如：内容审核员"
                  onChange={(e) => N(e.target.value)}
                />
              </div>
              <div className="av-role-field">
                <label htmlFor={fieldId + '-code'}>
                  角色编码<span>必填</span>
                </label>
                <Input
                  id={fieldId + '-code'}
                  required
                  disabled={busy}
                  maxLength={64}
                  className="av-role-code"
                  value={code}
                  placeholder="CONTENT_REVIEWER"
                  onChange={(e) => C(e.target.value.toUpperCase())}
                />
                <small>使用大写字母、数字和下划线，作为唯一标识。</small>
              </div>
              <div className="av-role-field av-role-field-wide">
                <label htmlFor={fieldId + '-description'}>
                  角色说明<span>选填</span>
                </label>
                <Input.TextArea
                  id={fieldId + '-description'}
                  disabled={busy}
                  value={description}
                  maxLength={300}
                  rows={2}
                  placeholder="简要描述该岗位的工作职责与授权用途"
                  onChange={(e) => D(e.target.value)}
                />
              </div>
            </div>
            <div className="av-role-status">
              <div>
                <span className="av-role-status-dot" data-enabled={enabled} />
                <div>
                  <strong>{enabled ? '角色已启用' : '角色已停用'}</strong>
                  <p>
                    {enabled
                      ? '关联管理员可使用此角色授予的权限。'
                      : '保留配置，暂不参与管理员权限计算。'}
                  </p>
                </div>
              </div>
              <Switch aria-label="启用角色" disabled={busy} checked={enabled} onChange={EN} />
            </div>
          </section>
          <section className="av-role-section" aria-labelledby={fieldId + '-permissions'}>
            <div className="av-role-section-heading">
              <h3 id={fieldId + '-permissions'}>
                <span>02</span>菜单授权
              </h3>
              <span className="av-role-selection">
                <Check size={13} />
                {selected} / {available.length} 项已选
              </span>
            </div>
            <p className="av-role-section-caption">勾选后授予对应业务菜单的查看与操作权限。</p>
            {catalog.isError && (
              <p role="alert">
                菜单目录加载失败 <Button onClick={() => catalog.refetch()}>重试</Button>
              </p>
            )}
            {catalog.isPending && <p className="muted">正在读取菜单目录…</p>}
            <div className="av-role-permissions">
              {Object.entries(groups).map(([group, keys]) => {
                const choices = keys.filter(
                  (key) => !['access', 'roles', 'permissions'].includes(key),
                );
                const count = menus.filter((key) => choices.includes(key)).length;
                return (
                  <section className="av-role-permission-card" key={group} aria-label={group}>
                    <div className="av-role-permission-heading">
                      <h4>{group}</h4>
                      <span data-selected={count > 0}>
                        {count}/{choices.length}
                      </span>
                    </div>
                    <Checkbox.Group
                      disabled={busy}
                      value={menus.filter((key) => keys.includes(key))}
                      onChange={(values) =>
                        M((old) => [
                          ...old.filter((key) => !keys.includes(key)),
                          ...(values as string[]),
                        ])
                      }
                      options={choices.map((value) => ({
                        value,
                        label: permissions[value] ?? value,
                      }))}
                    />
                  </section>
                );
              })}
            </div>
            <div className="av-role-access-note">
              <LockKeyhole size={14} />
              <span>管理员、角色及菜单权限的授权管理，仅限超级管理员。</span>
            </div>
          </section>
        </div>
        {error && (
          <p role="alert" className="av-role-error">
            {error}
          </p>
        )}
        <div className="av-role-footer">
          <span>
            <Layers3 size={14} />
            {selected ? `已配置 ${selected} 项菜单权限` : '尚未授予菜单权限'}
          </span>
          <div>
            <Button disabled={busy} onClick={close}>
              取消
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={busy}
              disabled={!name.trim() || !code.trim() || !catalog.data}
            >
              {r ? '保存修改' : '创建角色'}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
