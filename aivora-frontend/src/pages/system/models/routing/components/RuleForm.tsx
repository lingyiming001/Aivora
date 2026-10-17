import { useState } from 'react';
import { Button } from 'antd';
import axios from 'axios';
import { api } from '@/services/request';
import { type Model, type Rule } from '@/pages/system/models/routing/types';
import { tiers } from '@/constants/membership';

export function RuleForm({
  rule,
  models,
  close,
  saved,
}: {
  rule?: Rule;
  models: Model[];
  close: () => void;
  saved: () => void;
}) {
  const [helpers, H] = useState<number[]>(rule?.helper_model_ids ?? []),
    [minChars, MC] = useState(rule?.min_characters ?? 0);
  const [name, N] = useState(rule?.name ?? ''),
    [tier, T] = useState(rule?.user_tier ?? 'STANDARD'),
    [min, MI] = useState(rule?.min_stars ?? 1),
    [max, MA] = useState(rule?.max_stars?.toString() ?? ''),
    [model, M] = useState(
      rule?.model_id ??
        models.find(
          (m) =>
            ['CHAT', 'TEXT'].includes(m.model_type) &&
            m.composition_allowed &&
            m.enabled &&
            m.provider_enabled,
        )?.id ??
        0,
    ),
    [priority, P] = useState(rule?.priority ?? 0),
    [enabled, EN] = useState(rule?.enabled ?? true),
    [busy, B] = useState(false),
    [error, X] = useState('');
  async function save(e: React.FormEvent) {
    e.preventDefault();
    B(true);
    try {
      const body = {
        helperModelIds: helpers,
        minCharacters: minChars,
        name,
        userTier: tier,
        minStars: min,
        maxStars: max ? Number(max) : null,
        modelId: model,
        priority,
        enabled,
      };
      if (rule) await api.put('/admin/routes/' + rule.id, body);
      else await api.post('/admin/routes', body);
      saved();
      close();
    } catch (e) {
      X(axios.isAxiosError(e) ? e.response?.data?.message || '保存失败' : '保存失败');
    } finally {
      B(false);
    }
  }
  return (
    <form className="panel model-form" onSubmit={save}>
      <h3>{rule ? '编辑' : '新增'}路由规则</h3>
      <label>
        规则名称
        <input
          className="input"
          required
          maxLength={80}
          value={name}
          onChange={(e) => N(e.target.value)}
        />
      </label>
      <label>
        用户等级
        <select className="input" value={tier} onChange={(e) => T(e.target.value)}>
          {Object.entries(tiers).map(([v, n]) => (
            <option key={v} value={v}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <label>
        最低角色消耗
        <input
          className="input"
          type="number"
          min={1}
          required
          value={min}
          onChange={(e) => MI(Number(e.target.value))}
        />
      </label>
      <label>
        最高角色消耗（留空表示不限）
        <input
          className="input"
          type="number"
          min={min}
          value={max}
          onChange={(e) => MA(e.target.value)}
        />
      </label>
      <label>
        主回复模型
        <select
          className="input"
          required
          value={model}
          onChange={(e) => {
            const id = Number(e.target.value);
            M(id);
            H((old) =>
              models.find((m) => m.id === id)?.composition_allowed
                ? old.filter((x) => x !== id)
                : [],
            );
          }}
        >
          {models
            .filter((m) => ['CHAT', 'TEXT'].includes(m.model_type) && m.composition_allowed)
            .map((m) => (
              <option key={m.id} value={m.id}>
                {m.provider} / {m.model_name}
                {!m.enabled || !m.provider_enabled ? '（未启用）' : ''}
              </option>
            ))}
        </select>
      </label>
      <fieldset disabled={!models.find((m) => m.id === model)?.composition_allowed}>
        <legend>多模型组合</legend>
        <p className="muted helper">
          主模型须先选择阿里云百炼。最多选择 3
          个辅助文字/聊天模型并行生成建议，由主模型汇总。组合调用仅限阿里云百炼；低于长度门槛时仅调用主模型。每条回复按角色价格扣费一次，组合会增加供应商调用次数。
        </p>
        {models
          .filter(
            (m) =>
              ['CHAT', 'TEXT'].includes(m.model_type) && m.composition_allowed && m.id !== model,
          )
          .map((m) => (
            <label key={m.id}>
              <input
                type="checkbox"
                checked={helpers.includes(m.id)}
                disabled={!helpers.includes(m.id) && helpers.length >= 3}
                onChange={(e) => {
                  H((old) => (e.target.checked ? [...old, m.id] : old.filter((x) => x !== m.id)));
                }}
              />
              {m.provider} / {m.model_name}
            </label>
          ))}
        <label>
          启用组合的消息长度（0 表示每条）
          <input
            className="input"
            type="number"
            min={0}
            max={10000}
            value={minChars}
            onChange={(e) => MC(Number(e.target.value))}
          />
        </label>
      </fieldset>
      <label>
        优先级
        <input
          className="input"
          type="number"
          min={0}
          required
          value={priority}
          onChange={(e) => P(Number(e.target.value))}
        />
      </label>
      <label>
        <input type="checkbox" checked={enabled} onChange={(e) => EN(e.target.checked)} />
        启用规则
      </label>
      <div className="actions">
        <Button htmlType="submit" type="primary" loading={busy}>
          保存规则
        </Button>
        <Button onClick={close}>取消</Button>
      </div>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
