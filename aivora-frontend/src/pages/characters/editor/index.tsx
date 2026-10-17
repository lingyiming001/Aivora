import { useConfirm } from '@/components/ConfirmDialog';
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  Check,
  Coins,
  LoaderCircle,
  LockKeyhole,
  Sparkles,
  Upload,
  WandSparkles,
} from 'lucide-react';
import axios from 'axios';
import { api } from '@/services/request';
import { MessageText } from '@/components/MessageText/index';
import { type Character } from '@/pages/characters/types';
import { type Draft } from '@/pages/characters/editor/types';

export const categories = ['日常陪伴', '创意写作', '学习成长', '工作效率', '幻想世界'];

export const explain = (e: unknown) =>
  axios.isAxiosError(e)
    ? e.response?.data?.message || '操作失败，请重试'
    : e instanceof Error
      ? e.message
      : '操作失败，请重试';

export default function CharacterEditor() {
  const confirm = useConfirm();
  const { id } = useParams(),
    nav = useNavigate(),
    fileRef = useRef<HTMLInputElement>(null),
    requesting = useRef(false);
  const [name, N] = useState(''),
    [summary, S] = useState(''),
    [tags, T] = useState(''),
    [persona, P] = useState(''),
    [opening, O] = useState(''),
    [category, C] = useState(categories[0]),
    [avatar, A] = useState('/images/character-night.png'),
    [prompt, SP] = useState(''),
    [busy, B] = useState(false),
    [uploading, U] = useState(false),
    [generating, G] = useState(false),
    [error, E] = useState(''),
    [notice, F] = useState(''),
    [idea, I] = useState(''),
    [draft, D] = useState<Draft | null>(null),
    [created, ID] = useState<number | null>(null);
  const edit = useQuery({
    queryKey: ['edit-character', id],
    enabled: !!id,
    refetchOnWindowFocus: false,
    queryFn: async () =>
      (
        await api.get<Character & { persona: string; system_prompt: string }>(
          '/characters/' + id + '/edit',
        )
      ).data,
  });
  useEffect(() => {
    const c = edit.data;
    if (c) {
      N(c.name);
      S(c.summary);
      T(c.tags);
      P(c.persona);
      O(c.opening);
      C(c.category || categories[0]);
      A(c.avatar_url || '/images/character-night.png');
      SP(c.system_prompt);
    }
  }, [edit.data]);
  const blocked = busy || generating || uploading;
  async function upload(file?: File) {
    if (!file || blocked) return;
    E('');
    F('');
    if (
      !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
      file.size > 5 * 1024 * 1024
    ) {
      E('请选择 5 MB 以内的 PNG、JPEG 或 WebP 图片');
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
      const scale = Math.min(1, 512 / Math.max(img.width, img.height));
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      const ctx = canvas.getContext('2d')!;
      ctx.fillStyle = '#edf2ee';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      let encoded = '';
      for (const quality of [0.88, 0.75, 0.6, 0.45]) {
        encoded = canvas.toDataURL('image/jpeg', quality);
        if (encoded.length < 290000) break;
      }
      if (encoded.length >= 290000) throw new Error('图片细节过多，请换一张图片');
      A(encoded);
      F('图片已准备好，保存角色后生效。');
    } catch (e) {
      E(explain(e));
    } finally {
      if (url) URL.revokeObjectURL(url);
      U(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }
  async function generate() {
    if (blocked || requesting.current || !idea.trim()) return;
    requesting.current = true;
    G(true);
    E('');
    F('');
    try {
      const { data } = await api.post<Draft>(
        '/characters/generate',
        { idea: idea.trim() },
        { timeout: 150000 },
      );
      D(data);
    } catch (e) {
      E(explain(e));
    } finally {
      G(false);
      requesting.current = false;
    }
  }
  async function applyDraft() {
    if (!draft) return;
    const apply = () => {
      N(draft.name);
      S(draft.summary);
      P(draft.persona);
      SP(draft.systemPrompt);
      O(draft.opening);
      C(draft.category);
      T(draft.tags);
      D(null);
      F('AI 草稿已应用，可继续调整每个细节。');
    };
    if (name || summary || persona || opening) {
      if (
        !(await confirm({
          title: '应用 AI 草稿？',
          description: 'AI 草稿将替换当前文字设定，包括名称、简介、性格与开场白。',
          note: '已上传的角色图片会保留。',
          confirmLabel: '应用草稿',
          cancelLabel: '保留当前内容',
        }))
      )
        return;
    }
    apply();
  }
  async function save(start: boolean) {
    if (blocked) return;
    B(true);
    E('');
    try {
      let characterId = created ?? (Number(id) || 0);
      const payload = {
        name: name.trim(),
        summary: summary.trim(),
        tags,
        persona: persona.trim(),
        opening: opening.trim(),
        category,
        avatarUrl: avatar,
        systemPrompt: prompt,
      };
      if (id || created) await api.put('/characters/' + characterId, payload);
      else {
        const { data } = await api.post('/characters', payload);
        characterId = data.id;
        ID(data.id);
      }
      if (start) {
        const { data } = await api.post('/chats', { characterId });
        nav('/chat/' + data.id);
      } else nav('/my-characters');
    } catch (e) {
      E(explain(e));
    } finally {
      B(false);
    }
  }
  if (id && edit.isPending) return <main className="client-page">正在读取角色档案…</main>;
  if (id && (edit.isError || edit.data?.visibility !== 'PRIVATE'))
    return (
      <main className="client-page">
        仅可编辑自己的私密角色。<Link to="/my-characters">返回我的角色</Link>
      </main>
    );
  return (
    <main className="client-page editor-page atelier-page">
      <Link className="back-link" to="/my-characters">
        <ArrowLeft size={16} />
        我的角色
      </Link>
      <header className="atelier-heading">
        <div>
          <p className="eyebrow">THE CHARACTER ATELIER</p>
          <h1>{id ? '雕琢你的角色。' : '让一个想法，成为一个角色。'}</h1>
          <p>从一句灵感开始，赋予 TA 模样、性格与第一句话。</p>
        </div>
        <span className="atelier-private">
          <LockKeyhole size={14} />
          仅自己可见
        </span>
      </header>
      {error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}
      <div className="atelier-layout">
        <div className="atelier-workbench">
          <section className="idea-studio">
            <div className="atelier-section-title">
              <span className="atelier-section-icon">
                <WandSparkles size={21} />
              </span>
              <div>
                <h2>一句话，创造一个世界</h2>
                <p>告诉 AI 你想遇见谁，细节交给灵感。</p>
              </div>
              <span className="ai-label">AI 创作</span>
            </div>
            <label className="sr-only" htmlFor="character-idea">
              一句话角色灵感
            </label>
            <textarea
              id="character-idea"
              maxLength={500}
              value={idea}
              onChange={(e) => I(e.target.value)}
              disabled={blocked}
              placeholder="例如：一位在海边经营旧书店的温柔旅行作家，喜欢星空与未完的故事。"
              rows={3}
            />
            <div className="idea-footer">
              <span>{idea.length} / 500</span>
              <button
                type="button"
                className="atelier-primary"
                onClick={generate}
                disabled={blocked || !idea.trim()}
              >
                {generating ? <LoaderCircle className="spin" size={16} /> : <Sparkles size={16} />}{' '}
                {generating ? '正在构思角色…' : '生成角色草稿'}
              </button>
            </div>
            {!idea && (
              <div className="idea-suggestions">
                {['温柔的海边书店店主', '来自未来的星际旅行者', '耐心幽默的英语陪练'].map(
                  (text) => (
                    <button type="button" key={text} disabled={blocked} onClick={() => I(text)}>
                      {text}
                      <ArrowRight size={12} />
                    </button>
                  ),
                )}
              </div>
            )}
            {draft && (
              <div className="generated-draft">
                <span>
                  <Check size={15} />
                  草稿已完成
                </span>
                <h3>{draft.name}</h3>
                <p>{draft.summary}</p>
                <details>
                  <summary>查看设定与开场白</summary>
                  <p>{draft.persona}</p>
                  <MessageText content={draft.opening} />
                </details>
                <button
                  type="button"
                  className="atelier-primary"
                  onClick={applyDraft}
                  disabled={blocked}
                >
                  应用到角色档案
                  <ArrowRight size={16} />
                </button>
              </div>
            )}
          </section>
          <form
            className="character-form atelier-form"
            onSubmit={(e) => {
              e.preventDefault();
              void save(true);
            }}
          >
            <fieldset disabled={blocked}>
              <section className="atelier-form-section">
                <div className="atelier-section-title">
                  <span className="atelier-number">01</span>
                  <div>
                    <h2>模样与身份</h2>
                    <p>一个名字，一张面孔，一眼就能记住。</p>
                  </div>
                </div>
                <div className="character-upload">
                  <button
                    className="upload-portrait"
                    type="button"
                    aria-label="上传角色图片"
                    onClick={() => fileRef.current?.click()}
                  >
                    <img src={avatar} alt="角色图片预览" />
                    <span>
                      {uploading ? (
                        <LoaderCircle className="spin" size={19} />
                      ) : (
                        <Camera size={19} />
                      )}
                    </span>
                  </button>
                  <div>
                    <h3>角色图片</h3>
                    <p>
                      PNG、JPEG、WebP · 最大 5 MB
                      <br />
                      保留完整画面，按原比例压缩。
                    </p>
                    <button
                      className="atelier-secondary"
                      type="button"
                      onClick={() => fileRef.current?.click()}
                    >
                      <Upload size={14} />
                      {uploading ? '正在处理…' : '上传图片'}
                    </button>
                    {avatar.startsWith('data:') && (
                      <button
                        className="upload-reset"
                        type="button"
                        onClick={() => A('/images/character-night.png')}
                      >
                        恢复默认
                      </button>
                    )}
                    <input
                      ref={fileRef}
                      className="sr-only"
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      aria-label="选择角色图片"
                      onChange={(e) => void upload(e.target.files?.[0])}
                    />
                  </div>
                </div>
                <div className="atelier-field-row">
                  <div>
                    <label htmlFor="character-name">角色名称</label>
                    <input
                      id="character-name"
                      className="input"
                      required
                      maxLength={100}
                      placeholder="为 TA 起一个名字"
                      value={name}
                      onChange={(e) => N(e.target.value)}
                    />
                  </div>
                  <div>
                    <label htmlFor="character-category">角色分类</label>
                    <select
                      id="character-category"
                      className="input"
                      value={category}
                      onChange={(e) => C(e.target.value)}
                    >
                      {categories.map((x) => (
                        <option key={x}>{x}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <label htmlFor="character-summary">一句话介绍</label>
                <input
                  id="character-summary"
                  className="input"
                  required
                  maxLength={1000}
                  placeholder="例如：收藏星光与故事的海边书店店主"
                  value={summary}
                  onChange={(e) => S(e.target.value)}
                />
                <label htmlFor="character-tags">
                  兴趣标签 <small>用逗号分隔</small>
                </label>
                <input
                  id="character-tags"
                  className="input"
                  maxLength={200}
                  placeholder="旅行，星空，旧书"
                  value={tags}
                  onChange={(e) => T(e.target.value)}
                />
              </section>
              <section className="atelier-form-section">
                <div className="atelier-section-title">
                  <span className="atelier-number">02</span>
                  <div>
                    <h2>性格与故事</h2>
                    <p>让每一次回应，都有属于 TA 的温度。</p>
                  </div>
                </div>
                <label htmlFor="character-persona">角色设定</label>
                <textarea
                  id="character-persona"
                  required
                  className="input"
                  maxLength={10000}
                  rows={6}
                  placeholder="TA 的经历、性格、爱好，以及与你的关系…"
                  value={persona}
                  onChange={(e) => P(e.target.value)}
                />
                <label htmlFor="character-opening">开场白</label>
                <textarea
                  id="character-opening"
                  required
                  className="input"
                  maxLength={2000}
                  rows={4}
                  placeholder="想象初次相遇，TA 会如何向你打招呼？"
                  value={opening}
                  onChange={(e) => O(e.target.value)}
                />
                <details className="atelier-advanced">
                  <summary>
                    细化说话风格 <span>可选</span>
                  </summary>
                  <label className="sr-only" htmlFor="character-style">
                    说话风格
                  </label>
                  <textarea
                    id="character-style"
                    className="input"
                    rows={3}
                    maxLength={10000}
                    value={prompt}
                    onChange={(e) => SP(e.target.value)}
                    placeholder="例如：语气自然温柔，回复简洁，偶尔引用诗句。"
                  />
                </details>
              </section>
            </fieldset>
            {notice && (
              <p className="atelier-notice" role="status">
                <Check size={16} />
                {notice}
              </p>
            )}
            <div className="atelier-save">
              <span>
                <LockKeyhole size={14} />
                保存为私密角色
              </span>
              <div>
                <button
                  className="atelier-secondary"
                  type="button"
                  disabled={
                    blocked || !name.trim() || !summary.trim() || !persona.trim() || !opening.trim()
                  }
                  onClick={() => void save(false)}
                >
                  仅保存
                </button>
                <button className="atelier-primary" disabled={blocked} type="submit">
                  {busy ? '正在保存…' : id || created ? '保存并开始对话' : '创建并开始对话'}
                  <ArrowRight size={16} />
                </button>
              </div>
            </div>
          </form>
        </div>
        <aside className="atelier-preview">
          <div className="preview-topline">
            <span>角色预览</span>
            <span>
              <i />
              实时更新
            </span>
          </div>
          <div className="atelier-preview-cover">
            <img src={avatar} alt={name || '角色预览'} />
            <span>{category}</span>
          </div>
          <div className="atelier-preview-body">
            <h2>{name || '等待一个名字'}</h2>
            <p>{summary || '每一个独特的角色，都从一个小小的想法开始。'}</p>
            <div className="role-tags">
              {tags
                .split(/[,，\s]+/)
                .filter(Boolean)
                .slice(0, 6)
                .map((tag, i) => (
                  <span key={i}># {tag}</span>
                ))}
            </div>
            <div className="preview-first-word">
              <span>初次见面</span>
              <MessageText content={opening || '第一句话，将从这里开始。'} />
            </div>
            <div className="preview-bottom">
              <span>
                <LockKeyhole size={13} />
                私密角色
              </span>
              <span>
                <Coins size={14} />
                {edit.data?.stars_per_message ?? 1} 星币 / 条
              </span>
            </div>
          </div>
        </aside>
      </div>
    </main>
  );
}
