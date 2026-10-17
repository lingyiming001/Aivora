import React, { useState } from 'react';
import { Button } from 'antd';
import { api } from '@/services/request';
import { type Provider, type Model } from '@/pages/system/models/types';
import { modelTypes } from '../constants';
import { explain } from '../helpers';

export function ModelForm({
  m,
  providers,
  refresh,
  onClose,
}: {
  m?: Model;
  providers: Provider[];
  refresh: () => void;
  onClose: () => void;
}) {
  const [type, MT] = useState(m?.model_type ?? 'CHAT');
  const [provider, P] = useState(m?.provider_id ?? providers[0]?.id ?? 0),
    [name, N] = useState(m?.model_name ?? ''),
    [stars, S] = useState(m?.star_per_request ?? 1),
    [enabled, E] = useState(m?.enabled ?? true),
    [isDefault, D] = useState(m?.is_default ?? false),
    [busy, B] = useState(false),
    [error, X] = useState('');
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    B(true);
    try {
      const payload = {
        modelType: type,
        providerId: provider,
        modelName: name,
        starPerRequest: stars,
        enabled,
        isDefault: enabled && isDefault && ['CHAT', 'TEXT'].includes(type),
      };
      if (m) await api.put('/admin/models/' + m.id, payload);
      else await api.post('/admin/models/configure', payload);
      refresh();
      onClose();
    } catch (e) {
      X(explain(e));
    } finally {
      B(false);
    }
  }
  return (
    <form className="panel model-form" onSubmit={submit}>
      <h2>{m ? '编辑模型' : '添加模型'}</h2>
      <label>
        供应商
        <select
          className="input"
          value={provider}
          disabled={!!m}
          onChange={(e) => P(Number(e.target.value))}
        >
          {providers.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        模型类型
        <select
          className="input"
          value={type}
          onChange={(e) => {
            MT(e.target.value);
            if (!['CHAT', 'TEXT'].includes(e.target.value)) D(false);
          }}
        >
          {Object.entries(modelTypes).map(([v, label]) => (
            <option key={v} value={v}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <p className="muted helper">
        文字模型用于文本生成，聊天模型用于多轮对话，ASR用于录音转文字，TTS用于朗读角色回复，多模态用于图片理解，图片模型用于文生图能力配置；媒体模型不参与文字聊天路由。
      </p>
      <label>
        模型 ID
        <input
          className="input"
          required
          maxLength={120}
          value={name}
          onChange={(e) => N(e.target.value)}
        />
      </label>
      <label>
        模型参考星币（不用于角色扣费）
        <input
          className="input"
          type="number"
          min={0}
          step={1}
          required
          value={stars}
          onChange={(e) => S(Number(e.target.value))}
        />
      </label>
      <label>
        <input type="checkbox" checked={enabled} onChange={(e) => E(e.target.checked)} /> 启用模型
      </label>
      <label>
        <input
          type="checkbox"
          disabled={!['CHAT', 'TEXT'].includes(type)}
          checked={isDefault}
          onChange={(e) => D(e.target.checked)}
        />{' '}
        设为默认路由
      </label>
      <div className="actions">
        <Button htmlType="submit" type="primary" loading={busy}>
          保存模型
        </Button>
        <Button onClick={onClose}>取消</Button>
      </div>
      <p role="alert">{error}</p>
    </form>
  );
}
