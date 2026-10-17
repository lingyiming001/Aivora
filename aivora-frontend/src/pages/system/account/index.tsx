import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Input, Tabs, message } from 'antd';
import { UserRound, LockKeyhole, Smartphone, ShieldCheck } from 'lucide-react';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { useAuth } from '@/hooks/useAuth';
import { explain } from '@/utils/actionError';
type Account = {
  id: number;
  email: string;
  username: string | null;
  nickname: string;
  bio: string;
  avatar_url: string;
  phone: string | null;
  admin_super: boolean;
  smsAvailable: boolean;
  developmentSms: boolean;
};
export default function AdminAccount() {
  const q = useQuery({
      queryKey: ['admin-account'],
      queryFn: async () => (await api.get<Account>('/admin/account')).data,
    }),
    [params, setParams] = useSearchParams();
  return (
    <main className="admin-main">
      <p className="eyebrow">ACCOUNT / SETTINGS</p>
      <h1>我的账号</h1>
      <p className="muted">管理个人资料、登录密码与绑定手机。</p>
      {q.isPending ? (
        <p>正在读取…</p>
      ) : q.isError ? (
        <p role="alert">
          账号资料读取失败 <Button onClick={() => q.refetch()}>重试</Button>
        </p>
      ) : (
        q.data && (
          <Tabs
            activeKey={params.get('tab') ?? 'profile'}
            onChange={(tab) => setParams({ tab })}
            items={[
              {
                key: 'profile',
                label: (
                  <span>
                    <UserRound size={14} /> 个人资料
                  </span>
                ),
                children: <Profile account={q.data} />,
              },
              {
                key: 'security',
                label: (
                  <span>
                    <LockKeyhole size={14} /> 修改密码
                  </span>
                ),
                children: <Password />,
              },
              {
                key: 'phone',
                label: (
                  <span>
                    <Smartphone size={14} /> 手机绑定
                  </span>
                ),
                children: <Phone account={q.data} />,
              },
            ]}
          />
        )
      )}
    </main>
  );
}
function useReLogin() {
  const nav = useNavigate();
  return (notice: string) => {
    useAuth.getState().setTokens(null, 'admin');
    message.success(notice);
    nav('/admin/login', { replace: true });
  };
}
function Profile({ account }: { account: Account }) {
  const [name, N] = useState(account.nickname),
    [bio, BIO] = useState(account.bio),
    [avatar, A] = useState(account.avatar_url),
    [busy, B] = useState(false),
    [error, E] = useState('');
  async function upload(file?: File) {
    if (!file) return;
    E('');
    let url = '';
    try {
      if (
        !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
        file.size > 5 * 1024 * 1024
      )
        throw new Error('请选择5MB以内的图片');
      url = URL.createObjectURL(file);
      const img = new Image();
      img.src = url;
      await img.decode();
      const canvas = document.createElement('canvas');
      const scale = Math.min(256 / img.width, 256 / img.height, 1);
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      A(canvas.toDataURL('image/jpeg', 0.7));
    } catch (e) {
      E(e instanceof Error ? e.message : '图片读取失败');
    } finally {
      if (url) URL.revokeObjectURL(url);
    }
  }
  async function save() {
    if (busy) return;
    B(true);
    E('');
    try {
      await api.put('/admin/account', { nickname: name.trim(), bio, avatarUrl: avatar });
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['admin-account'] }),
        qc.invalidateQueries({ queryKey: ['admin-session'] }),
      ]);
      message.success('资料已保存');
    } catch (e) {
      E(explain(e));
    } finally {
      B(false);
    }
  }
  return (
    <section className="panel admin-account-panel">
      <div className="account-panel-heading">
        <UserRound size={20} />
        <div>
          <h2>个人资料</h2>
          <p>这里的资料用于后台展示，不影响权限配置。</p>
        </div>
      </div>
      <form
        className="management-form-body"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <div className="admin-account-avatar">
          <img src={avatar} alt="管理员头像" />
          <label>
            更换头像
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              disabled={busy}
              onChange={(e) => {
                void upload(e.target.files?.[0]);
                e.target.value = '';
              }}
            />
          </label>
        </div>
        <label>
          登录邮箱
          <Input value={account.email} readOnly />
        </label>
        <label>
          显示名称
          <Input
            required
            maxLength={80}
            value={name}
            disabled={busy}
            onChange={(e) => N(e.target.value)}
          />
        </label>
        <label>
          个人简介
          <Input.TextArea
            rows={3}
            maxLength={300}
            value={bio}
            disabled={busy}
            onChange={(e) => BIO(e.target.value)}
          />
        </label>
        {error && (
          <p role="alert" className="auth-error">
            {error}
          </p>
        )}
        <div>
          <Button type="primary" htmlType="submit" loading={busy} disabled={!name.trim()}>
            保存资料
          </Button>
        </div>
      </form>
    </section>
  );
}
function Password() {
  const relogin = useReLogin(),
    [current, C] = useState(''),
    [next, N] = useState(''),
    [confirm, F] = useState(''),
    [busy, B] = useState(false),
    [error, E] = useState('');
  async function save() {
    if (busy) return;
    if (next !== confirm) {
      E('两次输入的新密码不一致');
      return;
    }
    B(true);
    E('');
    try {
      await api.put('/admin/account/password', { currentPassword: current, newPassword: next });
      relogin('密码已修改，请重新登录');
    } catch (e) {
      E(explain(e));
    } finally {
      B(false);
    }
  }
  return (
    <section className="panel admin-account-panel">
      <div className="account-panel-heading">
        <LockKeyhole size={20} />
        <div>
          <h2>修改登录密码</h2>
          <p>修改后所有设备的登录状态都会失效。</p>
        </div>
      </div>
      <form
        className="management-form-body"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <label>
          当前密码
          <Input.Password
            required
            autoComplete="current-password"
            value={current}
            disabled={busy}
            onChange={(e) => C(e.target.value)}
          />
        </label>
        <label>
          新密码
          <Input.Password
            required
            minLength={8}
            maxLength={64}
            autoComplete="new-password"
            value={next}
            disabled={busy}
            onChange={(e) => N(e.target.value)}
          />
          <small>8至64位，包含字母和数字。</small>
        </label>
        <label>
          确认新密码
          <Input.Password
            required
            autoComplete="new-password"
            value={confirm}
            disabled={busy}
            onChange={(e) => F(e.target.value)}
          />
        </label>
        {error && (
          <p role="alert" className="auth-error">
            {error}
          </p>
        )}
        <div>
          <Button
            type="primary"
            htmlType="submit"
            loading={busy}
            disabled={!current || !next || !confirm}
          >
            修改密码
          </Button>
        </div>
      </form>
    </section>
  );
}
function Phone({ account }: { account: Account }) {
  const relogin = useReLogin(),
    [password, P] = useState(''),
    [phone, N] = useState(''),
    [oldCode, O] = useState(''),
    [newCode, C] = useState(''),
    [busy, B] = useState(false),
    [sending, S] = useState(''),
    [error, E] = useState(''),
    [dev, D] = useState<Record<string, string>>({}),
    [cooldown, T] = useState({ OLD: 0, NEW: 0 });
  useEffect(() => {
    const timer = setInterval(
      () => T((x) => ({ OLD: Math.max(0, x.OLD - 1), NEW: Math.max(0, x.NEW - 1) })),
      1000,
    );
    return () => clearInterval(timer);
  }, []);
  async function code(purpose: 'OLD' | 'NEW') {
    if (sending || !password) return;
    S(purpose);
    E('');
    try {
      const { data } = await api.post<{ retryAfter: number; developmentCode?: string }>(
        '/admin/account/phone/code',
        { purpose, phone: purpose === 'NEW' ? phone : undefined, currentPassword: password },
      );
      T((old) => ({ ...old, [purpose]: data.retryAfter }));
      if (data.developmentCode) D((old) => ({ ...old, [purpose]: data.developmentCode! }));
    } catch (e) {
      E(explain(e));
    } finally {
      S('');
    }
  }
  async function save() {
    if (busy) return;
    B(true);
    E('');
    try {
      await api.put('/admin/account/phone', {
        phone,
        newCode,
        oldCode: account.phone ? oldCode : undefined,
        currentPassword: password,
      });
      relogin('手机绑定已更新，请重新登录');
    } catch (e) {
      E(explain(e));
    } finally {
      B(false);
    }
  }
  return (
    <section className="panel admin-account-panel">
      <div className="account-panel-heading">
        <Smartphone size={20} />
        <div>
          <h2>{account.phone ? '更换绑定手机' : '绑定手机号'}</h2>
          <p>
            {account.phone
              ? '当前绑定 ' + account.phone.slice(0, 3) + '****' + account.phone.slice(-4)
              : '绑定后可使用手机号登录后台。'}
          </p>
        </div>
      </div>
      <form
        className="management-form-body"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        {account.developmentSms && (
          <p className="account-dev-note">当前为开发验证码模式，不发送真实短信。</p>
        )}
        {!account.smsAvailable && <p role="status">短信服务尚未配置，暂时无法绑定或换绑手机。</p>}
        <label>
          当前登录密码
          <Input.Password
            required
            autoComplete="current-password"
            value={password}
            disabled={busy || !!sending}
            onChange={(e) => P(e.target.value)}
          />
        </label>
        {account.phone && (
          <label>
            原手机验证码
            <div className="account-code-row">
              <Input
                required
                inputMode="numeric"
                maxLength={6}
                value={oldCode}
                disabled={busy}
                onChange={(e) => O(e.target.value.replace(/\D/g, ''))}
              />
              <Button
                disabled={
                  !account.smsAvailable || busy || !!sending || !password || cooldown.OLD > 0
                }
                loading={sending === 'OLD'}
                onClick={() => void code('OLD')}
              >
                {cooldown.OLD ? cooldown.OLD + '秒后重发' : '获取原手机验证码'}
              </Button>
            </div>
          </label>
        )}
        <label>
          {account.phone ? '新手机号' : '手机号'}
          <Input
            required
            pattern="1[3-9][0-9]{9}"
            inputMode="tel"
            maxLength={11}
            value={phone}
            disabled={busy || !!sending}
            onChange={(e) => {
              N(e.target.value.replace(/\D/g, ''));
              C('');
              D((x) => ({ ...x, NEW: '' }));
            }}
          />
        </label>
        <label>
          新手机验证码
          <div className="account-code-row">
            <Input
              required
              inputMode="numeric"
              maxLength={6}
              value={newCode}
              disabled={busy}
              onChange={(e) => C(e.target.value.replace(/\D/g, ''))}
            />
            <Button
              disabled={
                !account.smsAvailable ||
                busy ||
                !!sending ||
                !password ||
                !/^1[3-9][0-9]{9}$/.test(phone) ||
                cooldown.NEW > 0
              }
              loading={sending === 'NEW'}
              onClick={() => void code('NEW')}
            >
              {cooldown.NEW ? cooldown.NEW + '秒后重发' : '获取验证码'}
            </Button>
          </div>
        </label>
        {account.developmentSms && (dev.OLD || dev.NEW) && (
          <p className="account-dev-note">
            {dev.OLD && '原手机验证码：' + dev.OLD + '　'}
            {dev.NEW && '新手机验证码：' + dev.NEW}
          </p>
        )}
        <p className="muted">绑定或换绑成功后，需要重新登录。</p>
        {error && (
          <p role="alert" className="auth-error">
            {error}
          </p>
        )}
        <div>
          <Button
            type="primary"
            htmlType="submit"
            loading={busy}
            disabled={
              !account.smsAvailable ||
              !!sending ||
              !password ||
              !phone ||
              !newCode ||
              (!!account.phone && !oldCode)
            }
          >
            确认{account.phone ? '换绑' : '绑定'}
          </Button>
        </div>
      </form>
    </section>
  );
}
