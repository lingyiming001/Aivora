import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
  Smartphone,
  Sparkles,
  UserRound,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { type Tokens } from '@/types/user';

export default function AuthPage({ admin = false }: { admin?: boolean }) {
  const nav = useNavigate(),
    setTokens = useAuth((s) => s.setTokens),
    tokens = useAuth((s) => (admin ? s.adminTokens : s.tokens));
  const [remember, REM] = useState(true),
    [method, M] = useState<'password' | 'phone'>('password'),
    [register, R] = useState(false),
    [account, A] = useState(''),
    [phone, P] = useState(''),
    [password, W] = useState(''),
    [code, C] = useState(''),
    [nickname, N] = useState(''),
    [username, U] = useState(''),
    [show, S] = useState(false),
    [busy, B] = useState(false),
    [sending, SS] = useState(false),
    [error, E] = useState(''),
    [notice, F] = useState(''),
    [devCode, D] = useState(''),
    [deadline, DL] = useState(0),
    [now, T] = useState(Date.now());
  useEffect(() => {
    if (tokens) nav(admin ? '/admin' : '/', { replace: true });
  }, [tokens, admin, nav]);
  const caps = useQuery({
    queryKey: ['auth-capabilities'],
    queryFn: async () =>
      (
        await axios.get<{ phoneLogin: boolean; developmentSms: boolean }>(
          '/api/auth/capabilities',
          { timeout: 10000 },
        )
      ).data,
    retry: false,
  });
  useEffect(() => {
    if (!deadline) return;
    const timer = setInterval(() => T(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [deadline]);
  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));
  const prefix = admin ? '/api/auth/admin' : '/api/auth';
  const explain = (e: unknown) =>
    axios.isAxiosError(e)
      ? e.response?.data?.message || '连接失败，请稍后重试'
      : '暂时无法登录，请稍后重试';
  function mode(v: 'password' | 'phone') {
    M(v);
    R(false);
    E('');
    F('');
    D('');
    C('');
  }
  async function send() {
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      E('请输入正确的 11 位手机号');
      return;
    }
    SS(true);
    E('');
    D('');
    try {
      const { data } = await axios.post(prefix + '/sms/send', { phone }, { timeout: 10000 });
      DL(Date.now() + data.retryAfter * 1000);
      T(Date.now());
      D(data.developmentCode ?? '');
      F(
        data.developmentCode
          ? '开发模式：未发送真实短信，请使用下方测试验证码。'
          : '验证码已发送，5 分钟内有效。',
      );
    } catch (e) {
      E(explain(e));
    } finally {
      SS(false);
    }
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    B(true);
    E('');
    try {
      const url = register
        ? '/api/auth/register'
        : method === 'phone'
          ? prefix + '/sms/login'
          : prefix + '/login';
      const payload = register
        ? { email: account, password, nickname, username: username || null }
        : method === 'phone'
          ? { phone, code }
          : { account, password };
      const { data } = await axios.post<Tokens>(url, payload, { timeout: 15000 });
      setTokens(data, admin ? 'admin' : 'user', false, remember);
      nav(admin ? '/admin' : '/', { replace: true });
    } catch (e) {
      E(explain(e));
    } finally {
      B(false);
    }
  }
  return (
    <main className={'auth-page ' + (admin ? 'auth-admin' : '')}>
      <section className="auth-story">
        <Link className="auth-brand" to="/">
          <span className="auth-brand-icon">
            <img src="/images/aivora-logo.png" alt="Aivora Logo" width={60} height={60} />
          </span>
          <div>
            Aivora<small>{admin ? '运营工作台' : 'AIVORA'}</small>
          </div>
        </Link>
        <div className="auth-story-copy">
          <span className="auth-kicker">
            {admin ? 'A THOUGHTFUL SPACE TO MANAGE' : 'A LITTLE SPARK. A NEW STORY.'}
          </span>
          <h2>
            {admin ? (
              <>
                让每一场对话，
                <br />
                都被用心照顾。
              </>
            ) : (
              <>
                每一种想象，
                <br />
                都有回响。
              </>
            )}
          </h2>
          <p>
            {admin
              ? '在这里管理角色、连接模型，陪伴每一个灵感发生。'
              : '在忙碌的世界里，留一点时间给好奇心。\n你的下一段故事，就从一声问候开始。'}
          </p>
          <div className="auth-story-note">
            <Sparkles size={18} /> {admin ? '有序运营，安心连接' : '遇见同频的人物，开启新的可能'}
          </div>
        </div>
        <span className="auth-story-footer">AIVORA · MEET YOUR NEXT CONVERSATION</span>
      </section>
      <section className="auth-content">
        <div className="auth-topline">
          <Link to="/">
            <ArrowLeft size={16} />
            返回首页
          </Link>
          {admin && (
            <Link to="/login">
              用户登录
              <ArrowRight size={14} />
            </Link>
          )}
        </div>
        <div className="auth-form-wrap">
          <div className="auth-badge">
            {admin ? <ShieldCheck size={18} /> : <Sparkles size={18} />}{' '}
            {admin ? '管理员专属入口' : '给想象一个回应'}
          </div>
          <h1>{register ? '创建你的Aivora账号' : admin ? '欢迎回到工作台' : '欢迎回到Aivora'}</h1>
          <p className="auth-intro">
            {register
              ? '只需几步，就能开启你的第一场对话。'
              : admin
                ? '登录后，继续照顾这个充满灵感的世界。'
                : '让我们继续那段还没讲完的故事。'}
          </p>
          {!register && (
            <div className="auth-tabs" role="tablist" aria-label="登录方式">
              <button
                type="button"
                role="tab"
                aria-selected={method === 'password'}
                onClick={() => mode('password')}
              >
                <UserRound size={17} />
                账号密码
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={method === 'phone'}
                onClick={() => mode('phone')}
              >
                <Smartphone size={17} />
                手机验证码
              </button>
            </div>
          )}
          <form onSubmit={submit} className="auth-form">
            {method === 'phone' && !register ? (
              <>
                <label htmlFor="login-phone">手机号码</label>
                <div className="auth-field">
                  <span className="phone-prefix">+86</span>
                  <input
                    id="login-phone"
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    maxLength={11}
                    required
                    pattern="1[3-9][0-9]{9}"
                    placeholder={admin ? '管理员绑定的手机号' : '请输入手机号'}
                    value={phone}
                    onChange={(e) => {
                      P(e.target.value.replace(/\D/g, ''));
                      D('');
                      F('');
                      C('');
                    }}
                  />
                </div>
                <label htmlFor="login-code">验证码</label>
                <div className="auth-field">
                  <LockKeyhole size={18} />
                  <input
                    id="login-code"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    pattern="[0-9]{6}"
                    required
                    placeholder="请输入 6 位验证码"
                    value={code}
                    onChange={(e) => C(e.target.value.replace(/\D/g, ''))}
                  />
                  <button
                    type="button"
                    className="send-code"
                    disabled={sending || remaining > 0 || !caps.data?.phoneLogin}
                    onClick={send}
                  >
                    {sending ? '发送中…' : remaining > 0 ? remaining + ' 秒后重试' : '获取验证码'}
                  </button>
                </div>
                {caps.isPending ? (
                  <p className="auth-hint">正在检查验证码服务…</p>
                ) : !caps.data?.phoneLogin ? (
                  <p className="auth-hint">短信服务暂不可用，请使用账号密码登录。</p>
                ) : (
                  caps.data.developmentSms && (
                    <div className="dev-notice">
                      <strong>开发测试模式</strong>
                      <p>
                        {admin
                          ? '本地管理员测试手机号：19900000000。只允许已绑定的管理员账号。'
                          : '可用 19900000001 体验，首次赠送 200 测试星币；其他未注册手机号验证后自动创建账号。'}
                      </p>
                    </div>
                  )
                )}
                {notice && (
                  <p className="auth-hint" role="status">
                    {notice}
                  </p>
                )}
                {devCode && (
                  <div className="dev-code">
                    本次测试验证码 <strong>{devCode}</strong>
                    <button type="button" onClick={() => C(devCode)}>
                      填入验证码
                    </button>
                  </div>
                )}
              </>
            ) : (
              <>
                <label htmlFor="login-account">{register ? '邮箱地址' : '登录账号'}</label>
                <div className="auth-field">
                  <UserRound size={18} />
                  <input
                    id="login-account"
                    type={register ? 'email' : 'text'}
                    autoComplete="username"
                    required
                    placeholder={register ? '请输入邮箱' : '用户名 / 邮箱 / 已绑定手机号'}
                    value={account}
                    onChange={(e) => A(e.target.value)}
                  />
                </div>
                {register && (
                  <>
                    <label htmlFor="login-username">
                      用户名 <small>可选</small>
                    </label>
                    <div className="auth-field">
                      <input
                        id="login-username"
                        placeholder="3–32 位字母、数字或下划线，字母开头"
                        pattern="[a-zA-Z][a-zA-Z0-9_]{2,31}"
                        value={username}
                        onChange={(e) => U(e.target.value)}
                      />
                    </div>
                    <label htmlFor="login-nickname">昵称</label>
                    <div className="auth-field">
                      <input
                        id="login-nickname"
                        placeholder="希望大家怎么称呼你？"
                        maxLength={80}
                        required
                        value={nickname}
                        onChange={(e) => N(e.target.value)}
                      />
                    </div>
                  </>
                )}
                <label htmlFor="login-password">密码</label>
                <div className="auth-field">
                  <LockKeyhole size={18} />
                  <input
                    id="login-password"
                    type={show ? 'text' : 'password'}
                    minLength={register ? 8 : undefined}
                    maxLength={72}
                    autoComplete={register ? 'new-password' : 'current-password'}
                    required
                    placeholder={register ? '设置密码，至少 8 位' : '请输入密码'}
                    value={password}
                    onChange={(e) => W(e.target.value)}
                  />
                  <button
                    className="password-visibility"
                    type="button"
                    aria-label={show ? '隐藏密码' : '显示密码'}
                    onClick={() => S(!show)}
                  >
                    {show ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </>
            )}
            {error && (
              <p className="auth-error" role="alert">
                {error}
              </p>
            )}
            <label className="auth-remember">
              <input
                type="checkbox"
                checked={remember}
                disabled={busy}
                onChange={(e) => REM(e.target.checked)}
              />
              <span>记住我</span>
              <small>7 天内免登录</small>
            </label>
            <button
              type="submit"
              className="auth-submit"
              disabled={busy || (method === 'phone' && !register && !caps.data?.phoneLogin)}
            >
              {busy ? '正在验证…' : register ? '创建账号' : admin ? '登录工作台' : '开启我的故事'}
              <ArrowRight size={18} />
            </button>
            {!admin && (
              <p className="auth-switch">
                {register ? '已经有账号？' : '第一次来到Aivora？'}
                <button
                  type="button"
                  onClick={() => {
                    R(!register);
                    M('password');
                    E('');
                  }}
                >
                  {register ? '直接登录' : '创建账号'}
                </button>
              </p>
            )}
          </form>
          <p className="auth-security">
            <Check size={14} />
            {admin ? '仅限授权管理员，登录权限与用户端独立' : '你的会话与个人信息，由你掌握'}
          </p>
        </div>
        <footer className="auth-footer">Aivora · 给想象一个回应</footer>
      </section>
    </main>
  );
}
