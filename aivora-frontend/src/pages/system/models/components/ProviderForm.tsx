import { useId, useState } from 'react';
import { Button, Input, Modal, Switch, Tag } from 'antd';
import { KeyRound } from 'lucide-react';
import { api } from '@/services/request';
import { useConfirm } from '@/components/ConfirmDialog';
import { type Provider } from '../types';
import { explain } from '../helpers';

export function ProviderForm({
  p,
  refresh,
  onClose,
}: {
  p?: Provider;
  refresh: () => void;
  onClose: () => void;
}) {
  const id = useId(),
    confirm = useConfirm();
  const [name, N] = useState(p?.name ?? ''),
    [url, U] = useState(p?.base_url ?? ''),
    [key, K] = useState(''),
    [enabled, EN] = useState(p?.enabled ?? false),
    [busy, B] = useState(false),
    [error, E] = useState('');
  async function save(clearKey = false) {
    if (busy) return;
    if (
      clearKey &&
      !(await confirm({
        title: '移除供应商密钥？',
        description: '该供应商将停用，关联模型将暂不可用。',
        confirmLabel: '移除密钥',
        danger: true,
      }))
    )
      return;
    B(true);
    E('');
    try {
      const body = {
        name: name.trim(),
        baseUrl: url.trim(),
        apiKey: key,
        enabled: clearKey ? false : enabled,
        clearKey,
      };
      if (p) await api.put('/admin/providers/' + p.id, body);
      else await api.post('/admin/providers', { ...body, kind: 'OPENAI_COMPATIBLE' });
      K('');
      refresh();
      onClose();
    } catch (e) {
      E(explain(e));
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
      onCancel={() => !busy && onClose()}
      title={
        <div className="management-modal-title">
          <KeyRound size={22} />
          <div>
            <h2>{p ? '编辑供应商' : '新增供应商'}</h2>
            <p>集中管理服务连接与加密凭证</p>
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
          <label htmlFor={id + '-name'}>
            供应商名称
            <Input
              id={id + '-name'}
              required
              maxLength={80}
              value={name}
              disabled={busy}
              placeholder="例如：企业模型服务"
              onChange={(e) => N(e.target.value)}
            />
          </label>
          <div className="management-protocol">
            <span>接入协议</span>
            <Tag>OpenAI 兼容 API</Tag>
          </div>
          <label htmlFor={id + '-url'}>
            Base URL
            <Input
              id={id + '-url'}
              required
              maxLength={500}
              value={url}
              disabled={busy}
              placeholder="https://api.example.com/v1"
              onChange={(e) => U(e.target.value)}
            />
            <small>填写 API 根地址，包含版本路径，不含 /chat/completions。</small>
          </label>
          <label htmlFor={id + '-key'}>
            API Key
            <Input.Password
              id={id + '-key'}
              maxLength={4096}
              autoComplete="new-password"
              value={key}
              disabled={busy}
              onChange={(e) => K(e.target.value)}
              placeholder={p?.key_configured ? '已加密保存，留空保留原密钥' : '输入供应商 API Key'}
            />
          </label>
          <div className="management-switch-row">
            <div>
              <strong>启用供应商</strong>
              <p>启用后可使用关联模型；新供应商保存后请添加模型。</p>
            </div>
            <Switch aria-label="启用供应商" checked={enabled} disabled={busy} onChange={EN} />
          </div>
          {error && (
            <p role="alert" className="auth-error">
              {error}
            </p>
          )}
        </div>
        <div className="management-form-footer">
          {p?.key_configured && (
            <Button danger disabled={busy} onClick={() => void save(true)}>
              移除密钥
            </Button>
          )}
          <span className="management-footer-spacer" />
          <Button disabled={busy} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={busy}
            disabled={!name.trim() || !url.trim() || (enabled && !p?.key_configured && !key.trim())}
          >
            {p ? '保存修改' : '创建供应商'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
