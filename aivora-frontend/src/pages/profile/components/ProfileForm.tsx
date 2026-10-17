import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Camera, Coins, UserRound, ArrowUpRight } from 'lucide-react';
import axios from 'axios';
import { updateProfile } from '../api';
import { qc } from '@/services/queryClient';
import { type Profile } from '@/pages/profile/types';
import { levels } from '@/constants/user';

export function ProfileForm({ profile: p }: { profile: Profile }) {
  const [nickname, N] = useState(p.nickname),
    [bio, BIO] = useState(p.bio),
    [avatar, A] = useState(p.avatar_url),
    [busy, B] = useState(false),
    [uploading, U] = useState(false),
    [error, E] = useState(''),
    [notice, S] = useState('');
  async function upload(file?: File) {
    if (!file) return;
    E('');
    S('');
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      E('请选择 5 MB 以内的 PNG 或 JPEG 图片');
      return;
    }
    U(true);
    let url = '';
    try {
      url = URL.createObjectURL(file);
      const img = new Image();
      img.src = url;
      await img.decode();
      const canvas = document.createElement('canvas');
      canvas.width = 256;
      canvas.height = 256;
      const ctx = canvas.getContext('2d')!;
      ctx.fillStyle = '#eef4ef';
      ctx.fillRect(0, 0, 256, 256);
      const edge = Math.min(img.width, img.height);
      ctx.drawImage(
        img,
        (img.width - edge) / 2,
        (img.height - edge) / 2,
        edge,
        edge,
        0,
        0,
        256,
        256,
      );
      A(canvas.toDataURL('image/jpeg', 0.85));
    } catch {
      E('图片读取失败，请换一张图片');
    } finally {
      if (url) URL.revokeObjectURL(url);
      U(false);
    }
  }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    B(true);
    E('');
    S('');
    try {
      await updateProfile({ nickname: nickname.trim(), bio: bio.trim(), avatarUrl: avatar });
      await qc.invalidateQueries({ queryKey: ['profile'] });
      S('个人资料已保存');
    } catch (e) {
      E(axios.isAxiosError(e) ? e.response?.data?.message || '保存失败' : '保存失败');
    } finally {
      B(false);
    }
  }
  return (
    <div className="profile-grid">
      <form className="panel profile-form" onSubmit={save}>
        <h2>个人资料</h2>
        <div className="profile-avatar-edit">
          <img className="profile-avatar" src={avatar} alt="头像预览" />
          <div>
            <label className="avatar-upload">
              <Camera size={17} />
              {uploading ? '正在处理图片…' : '上传头像'}
              <input
                type="file"
                accept="image/png,image/jpeg"
                disabled={busy || uploading}
                onChange={(e) => {
                  void upload(e.target.files?.[0]);
                  e.target.value = '';
                }}
              />
            </label>
            <p className="muted helper">PNG / JPEG，最大 5 MB，自动裁剪为方形。</p>
          </div>
        </div>
        <div className="avatar-presets" aria-label="选择预设头像">
          {[1, 2, 3, 4, 5, 6].map((n) => (
            <button
              key={n}
              type="button"
              aria-label={'预设头像 ' + n}
              aria-pressed={avatar === '/images/profile-' + n + '.svg'}
              onClick={() => {
                A('/images/profile-' + n + '.svg');
                S('');
              }}
            >
              <img src={'/images/profile-' + n + '.svg'} alt="" />
            </button>
          ))}
        </div>
        <label htmlFor="profile-nickname">昵称</label>
        <input
          id="profile-nickname"
          className="input"
          required
          maxLength={80}
          value={nickname}
          onChange={(e) => {
            N(e.target.value);
            S('');
          }}
        />
        <label htmlFor="profile-bio">个人简介</label>
        <textarea
          id="profile-bio"
          className="input"
          rows={4}
          maxLength={300}
          placeholder="介绍一下自己喜欢的故事与灵感…"
          value={bio}
          onChange={(e) => {
            BIO(e.target.value);
            S('');
          }}
        />
        <small className="muted">{bio.length} / 300</small>
        {error && (
          <p role="alert" className="auth-error">
            {error}
          </p>
        )}
        {notice && (
          <p role="status" className="profile-success">
            {notice}
          </p>
        )}
        <button
          className="auth-submit"
          type="submit"
          disabled={busy || uploading || !nickname.trim()}
        >
          {busy ? '正在保存…' : '保存个人资料'}
        </button>
      </form>
      <aside className="profile-aside">
        <section className="panel">
          <h2>
            <UserRound size={20} />
            账号信息
          </h2>
          <dl>
            {[
              ['账号 ID', p.id],
              ['用户名', p.username || '未设置'],
              ['邮箱', /@(phone|dev)\.invalid$/.test(p.email) ? '未绑定' : p.email],
              ['手机号', p.phone || '未绑定'],
              ['会员等级', levels[p.membership_tier]],
              ['加入时间', new Date(p.created_at).toLocaleDateString('zh-CN')],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          <p className="muted helper">登录账号和会员等级由系统管理。</p>
        </section>
        <Link className="panel profile-shortcut" to="/my-characters">
          <UserRound />
          <span>
            <strong>我的角色卡</strong>
            <small>{p.character_count} 张角色卡 · 查看、编辑与投稿</small>
          </span>
          <ArrowUpRight />
        </Link>
        <Link className="panel profile-shortcut" to="/wallet">
          <Coins />
          <span>
            <strong>{p.balance} 星币</strong>
            <small>查看钱包与消费记录</small>
          </span>
          <ArrowUpRight />
        </Link>
      </aside>
    </div>
  );
}
