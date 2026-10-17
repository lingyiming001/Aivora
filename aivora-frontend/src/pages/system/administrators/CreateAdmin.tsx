import { useState } from 'react';
import { Modal, Input, Button, Switch, Select } from 'antd';
import { ShieldPlus } from 'lucide-react';
import { api } from '@/services/request';
import { explain } from '@/utils/actionError';
import { type Role } from '../access/types';
export default function CreateAdmin({
  roles,
  close,
  saved,
}: {
  roles: Role[];
  close: () => void;
  saved: () => void;
}) {
  const [email, E] = useState(''),
    [name, N] = useState(''),
    [password, P] = useState(''),
    [enabled, EN] = useState(true),
    [ids, I] = useState<number[]>([]),
    [busy, B] = useState(false),
    [error, ERR] = useState('');
  async function save() {
    if (busy) return;
    B(true);
    ERR('');
    try {
      await api.post('/admin/access', {
        email,
        nickname: name.trim(),
        password,
        enabled,
        roleIds: ids,
      });
      saved();
      close();
    } catch (e) {
      ERR(explain(e));
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
      maskClosable={false}
      footer={null}
      closable={!busy}
      keyboard={!busy}
      onCancel={() => !busy && close()}
      title={
        <div className="management-modal-title">
          <ShieldPlus size={23} />
          <div>
            <h2>新增管理员</h2>
            <p>创建独立账号，并按岗位授予业务权限</p>
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
        <div className="management-form-body">
          <label>
            管理员名称
            <Input
              required
              maxLength={80}
              value={name}
              disabled={busy}
              onChange={(e) => N(e.target.value)}
            />
          </label>
          <label>
            登录邮箱
            <Input
              required
              type="email"
              maxLength={320}
              value={email}
              disabled={busy}
              onChange={(e) => E(e.target.value)}
            />
          </label>
          <label>
            初始密码
            <Input.Password
              required
              minLength={8}
              maxLength={64}
              autoComplete="new-password"
              value={password}
              disabled={busy}
              onChange={(e) => P(e.target.value)}
            />
            <small>8至64位，包含字母和数字。请通过安全渠道交付账号。</small>
          </label>
          <label>
            关联角色
            <Select
              mode="multiple"
              placeholder="选择权限角色"
              value={ids}
              disabled={busy}
              onChange={I}
              options={roles.filter((r) => r.enabled).map((r) => ({ value: r.id, label: r.name }))}
            />
            <small>未分配角色时，仅能管理个人账号。新账号不会获得超级管理员权限。</small>
          </label>
          <div className="management-switch-row">
            <div>
              <strong>启用账号</strong>
              <p>停用账号无法登录后台。</p>
            </div>
            <Switch checked={enabled} disabled={busy} onChange={EN} />
          </div>
          {error && (
            <p role="alert" className="auth-error">
              {error}
            </p>
          )}
        </div>
        <div className="management-form-footer">
          <span className="management-footer-spacer" />
          <Button disabled={busy} onClick={close}>
            取消
          </Button>
          <Button type="primary" htmlType="submit" loading={busy}>
            创建管理员
          </Button>
        </div>
      </form>
    </Modal>
  );
}
