import { useConfirm } from '@/components/ConfirmDialog';
import { useEffect, useRef, useState } from 'react';
import { MessageText } from '@/components/MessageText/index';
import VoiceTools, { SpeechButton } from '@/pages/chat/components/VoiceTools';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  ArrowRight,
  Coins,
  Eraser,
  Trash2,
  Send,
  Sparkles,
  Copy,
  Check,
  ChevronDown,
  LoaderCircle,
  X,
} from 'lucide-react';
import { api } from '@/services/request';
import { accessToken } from '@/services/auth';
import { useAuth } from '@/hooks/useAuth';
import { Avatar } from '@/components/Avatar/index';
import { type Character } from '@/pages/characters/types';
import { type Message, type ChatAttachment } from '@/pages/chat/types';
import { useSpeechPlayback } from '@/pages/chat/hooks/useSpeechPlayback';
import AttachmentMenu from './components/AttachmentMenu';
import { ChatFiles } from './components/ChatFiles';
import { type FileAttachment } from './types';

export default function ChatPage() {
  const confirm = useConfirm();
  const { id } = useParams(),
    nav = useNavigate(),
    qc = useQueryClient();
  const user = useAuth((s) => s.tokens);
  const speech = useSpeechPlayback(id ?? '');
  const [attachment, setAttachment] = useState<ChatAttachment | null>(null);
  const [pendingImage, setPendingImage] = useState('');
  const [mediaBusy, setMediaBusy] = useState(false);
  const [files, setFiles] = useState<FileAttachment[]>([]);
  const [pendingFiles, setPendingFiles] = useState<FileAttachment[]>([]);
  const [fileBusy, setFileBusy] = useState(false);
  const attachmentTools = useRef<{ image: (file: File) => Promise<void>; draw: () => void } | null>(
    null,
  );
  const addFiles = useRef<((files: File[]) => Promise<void>) | null>(null);
  useEffect(() => {
    setAttachment(null);
    setPendingImage('');
    setMediaBusy(false);
    setFiles([]);
    setPendingFiles([]);
    setFileBusy(false);
  }, [id]);
  const [voiceMode, setVoiceMode] = useState({ chatId: id, enabled: false });
  const voiceChat = voiceMode.chatId === id && voiceMode.enabled && !!user;
  const voiceEnabled = useRef(voiceChat);
  const activeChat = useRef(id);
  voiceEnabled.current = voiceChat;
  activeChat.current = id;
  useEffect(() => {
    if (!voiceChat) speech.stop();
  }, [voiceChat, speech.stop]);
  function toggleVoice(enabled: boolean) {
    voiceEnabled.current = enabled;
    setVoiceMode({ chatId: id, enabled });
    if (!enabled) speech.stop();
  }
  const [msg, M] = useState(''),
    [answer, A] = useState(''),
    [busy, B] = useState(false),
    [error, E] = useState(''),
    [loginNeeded, L] = useState(false),
    [sending, SS] = useState(false),
    [pendingUser, PU] = useState(''),
    [copied, CP] = useState<string | null>(null),
    [away, AW] = useState(false);
  const messagesRef = useRef<HTMLDivElement>(null),
    nearBottom = useRef(true),
    copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const meta = useQuery({
    queryKey: ['chat-meta', id, !!user],
    queryFn: async () => (await api.get<Character & { balance: number }>('/chats/' + id)).data,
    retry: false,
  });
  const history = useQuery({
    queryKey: ['messages', id, !!user],
    queryFn: async () => (await api.get<Message[]>('/chats/' + id + '/messages')).data,
    retry: false,
  });
  const guest = useQuery({
    queryKey: ['guest-balance'],
    queryFn: async () =>
      (await api.get<{ active: boolean; balance: number; allowance: number }>('/guest/session'))
        .data,
    enabled: !user,
  });
  useEffect(() => {
    const el = messagesRef.current;
    if (el && nearBottom.current) el.scrollTop = el.scrollHeight;
  }, [answer, pendingUser, history.data]);
  useEffect(
    () => () => {
      if (copyTimer.current) clearTimeout(copyTimer.current);
    },
    [],
  );
  useEffect(() => {
    PU('');
    A('');
    M('');
    E('');
    L(false);
    nearBottom.current = true;
    AW(false);
  }, [id]);
  async function copy(content: string, key: string) {
    try {
      await navigator.clipboard.writeText(content);
      CP(key);
      if (copyTimer.current) clearTimeout(copyTimer.current);
      copyTimer.current = setTimeout(() => CP(null), 2000);
    } catch {
      E('复制失败，请手动选择文字复制');
    }
  }
  function scrollLatest() {
    const el = messagesRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
      nearBottom.current = true;
      AW(false);
    }
  }
  async function action(remove: boolean) {
    if (
      busy ||
      !(await confirm({
        title: remove ? '删除这段对话？' : '清空当前对话？',
        description: remove
          ? '这段对话及其消息记录将从你的对话列表中移除。'
          : '将移除当前消息记录，下一次对话会重新开始。',
        note: '已消耗的星币不会退还，请确认后继续。',
        confirmLabel: remove ? '确认删除' : '清空对话',
        danger: true,
      }))
    )
      return;
    B(true);
    E('');
    try {
      speech.stop();
      setAttachment(null);
      setPendingImage('');
      setFiles([]);
      setPendingFiles([]);
      if (remove) {
        await api.delete('/chats/' + id);
        await qc.invalidateQueries({ queryKey: ['my-chats'] });
        nav(user ? '/chats' : '/');
      } else {
        await api.post('/chats/' + id + '/clear');
        A('');
        await history.refetch();
        await qc.invalidateQueries({ queryKey: ['my-chats'] });
      }
    } catch (e) {
      E(e instanceof Error ? e.message : '操作失败');
    } finally {
      B(false);
    }
  }
  async function send() {
    if (
      busy ||
      mediaBusy ||
      (!msg.trim() && !attachment && !files.length) ||
      meta.data?.status === 'OFFLINE'
    )
      return;
    speech.stop();
    const content = msg.trim() || '请结合附件内容和我继续聊天。';
    const sentAttachment = attachment;
    const sentFiles = files;
    const previousReplyId = Math.max(
      0,
      ...(history.data ?? []).filter((x) => x.role === 'assistant').map((x) => x.id),
    );
    SS(true);
    B(true);
    E('');
    A('');
    L(false);
    try {
      const token = await accessToken();
      const res = await fetch('/api/chats/' + id + '/messages/stream', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: 'Bearer ' + token } : {}),
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          content,
          expectedStars: price,
          attachmentId: sentAttachment?.attachmentId,
          fileIds: sentFiles.map((file) => file.id),
        }),
      });
      if (!res.ok) {
        const data = await res.json();
        if (
          data.code === 'GUEST_QUOTA_EXHAUSTED' ||
          (!user && (res.status === 401 || res.status === 403))
        ) {
          L(true);
          throw new Error(data.message || '游客体验已结束，请登录后继续');
        }
        throw new Error(data.message || '请求失败');
      }
      PU(content);
      setPendingImage(sentAttachment?.image ?? '');
      setPendingFiles(sentFiles);
      setFiles([]);
      setAttachment(null);
      nearBottom.current = true;
      M('');
      const reader = res.body!.getReader(),
        decoder = new TextDecoder();
      let buf = '';
      let completed = false;
      let failed = false;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const parts = buf.split('\n\n');
        buf = parts.pop() || '';
        for (const part of parts) {
          const event = part.match(/event:\s*(\w+)/)?.[1],
            data = part.match(/data:\s*(.*)/)?.[1];
          if (!data) continue;
          const payload = JSON.parse(data);
          if (event === 'delta') A((s) => s + payload.text);
          if (event === 'done') completed = true;
          if (event === 'error') {
            failed = true;
            E(payload.message);
          }
        }
      }
      const result = await history.refetch();
      if (!result.isError) {
        A('');
        PU('');
        setPendingImage('');
        setPendingFiles([]);
        const reply = result.data
          ?.filter((x) => x.role === 'assistant' && x.id > previousReplyId)
          .sort((a, b) => b.id - a.id)[0];
        if (completed && !failed && reply && voiceEnabled.current && activeChat.current === id)
          void speech.play(reply.id);
      }
      await qc.invalidateQueries({ queryKey: ['my-chats'] });
    } catch (e) {
      E(e instanceof Error ? e.message : '发送失败，请重试');
    } finally {
      SS(false);
      B(false);
      void meta.refetch();
      if (!user) void guest.refetch();
    }
  }
  const c = meta.data,
    name = c?.name || '角色',
    price = c?.stars_per_message ?? 1;
  const unavailable = meta.isError || history.isError;
  return (
    <main className="conversation-page">
      <Link className="back-link" to="/">
        <ArrowLeft size={18} />
        发现角色
      </Link>
      <div className="conversation-layout">
        <section className="conversation-stage">
          <header className="conversation-header">
            <Avatar src={c?.avatar_url} name={name} />
            <div>
              <h1>{name}</h1>
              <p>
                <span />
                {c?.visibility === 'PRIVATE' ? '私密角色 · 仅自己可见' : 'AI 角色 · 一起开启故事'}
              </p>
            </div>
            <div className="conversation-header-actions">
              <span className="balance-pill">
                <Coins size={18} />
                {user ? (c?.balance ?? '…') : (guest.data?.balance ?? '…')}
              </span>
              <button
                aria-label="清空对话"
                disabled={busy || unavailable}
                onClick={() => action(false)}
              >
                <Eraser size={21} />
              </button>
              <button
                aria-label="删除对话"
                disabled={busy || unavailable}
                onClick={() => action(true)}
              >
                <Trash2 size={21} />
              </button>
            </div>
          </header>
          {!user && (
            <div className="visitor-strip">
              游客体验，按角色价格消耗体验星币。
              <Link to="/login">
                登录
                <ArrowRight size={13} />
              </Link>
            </div>
          )}
          <div
            ref={messagesRef}
            className="conversation-messages"
            aria-live="polite"
            aria-label="聊天消息"
            onScroll={() => {
              const el = messagesRef.current;
              if (el) {
                nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 90;
                AW(!nearBottom.current);
              }
            }}
          >
            {history.isPending || meta.isPending ? (
              <p className="muted">正在读取对话…</p>
            ) : unavailable ? (
              <div className="client-empty">
                <p>暂时无法读取这段对话。</p>
                <Link to="/">返回角色广场 →</Link>
              </div>
            ) : !history.data?.length && !answer ? (
              <>
                <div className="story-beginning">
                  <Sparkles size={32} />
                  <p>你与 {name} 的故事，从这里开始</p>
                </div>
                {c?.opening && (
                  <article className="opening-bubble">
                    <MessageText content={c.opening} />
                  </article>
                )}
              </>
            ) : (
              history.data?.map((x) => (
                <article
                  key={x.id}
                  className={'message-bubble ' + (x.role === 'user' ? 'message-user' : '')}
                >
                  {x.image && (
                    <img
                      className="chat-message-image"
                      src={x.image}
                      alt="聊天图片"
                      loading="lazy"
                    />
                  )}
                  {!!x.files?.length && <ChatFiles files={x.files} chatId={id!} />}
                  <MessageText content={x.content} />
                  {user && x.role === 'assistant' && (
                    <SpeechButton messageId={x.id} playback={speech} />
                  )}
                  <button
                    className="message-copy"
                    aria-label="复制消息"
                    onClick={() => void copy(x.content, String(x.id))}
                  >
                    {copied === String(x.id) ? <Check size={12} /> : <Copy size={12} />}
                  </button>
                </article>
              ))
            )}
            {pendingUser && (
              <article className="message-bubble message-user">
                {pendingImage && (
                  <img className="chat-message-image" src={pendingImage} alt="正在发送的图片" />
                )}
                {!!pendingFiles.length && <ChatFiles files={pendingFiles} chatId={id!} />}
                <MessageText content={pendingUser} />
              </article>
            )}
            {sending && !answer && (
              <div className="reply-status" role="status">
                <LoaderCircle className="spin" size={14} />
                正在构思回应…
              </div>
            )}
            {answer && (
              <article className="message-bubble">
                <MessageText content={answer} />
              </article>
            )}
          </div>
          {away && (
            <button className="jump-latest" onClick={scrollLatest}>
              <ChevronDown size={14} />
              最新消息
            </button>
          )}
          {loginNeeded && (
            <section className="login-required" role="alert">
              <strong>体验星币不足</strong>
              <p>登录后使用账户星币开启更多聊天。</p>
              <Link to="/login">
                登录 / 注册
                <ArrowRight size={16} />
              </Link>
            </section>
          )}
          {c?.status === 'OFFLINE' && (
            <p className="auth-error" role="status">
              此角色已下架，暂时无法继续对话。已有聊天记录仍可查看。
            </p>
          )}
          <footer className="conversation-input">
            <VoiceTools
              key={id}
              chatId={id!}
              voiceChat={voiceChat}
              onVoiceChatChange={toggleVoice}
              onRecordStart={speech.stop}
              onAttachment={setAttachment}
              onProcessingChange={setMediaBusy}
              attachmentTools={attachmentTools}
              disabled={busy || unavailable || c?.status === 'OFFLINE'}
              onText={(text) => M((old) => (old ? old + '\n' + text : text))}
            />
            <div
              className="conversation-composer"
              onDragOver={(e) => {
                e.preventDefault();
              }}
              onDrop={(e) => {
                e.preventDefault();
                void addFiles.current?.(Array.from(e.dataTransfer.files));
              }}
            >
              {(attachment || files.length > 0) && (
                <section className="composer-attachments" aria-label="待发送附件">
                  {attachment && (
                    <div className="composer-image-card">
                      <img
                        src={attachment.image}
                        alt={attachment.source === 'GENERATED' ? '待发送的生成图片' : '待发送图片'}
                      />
                      <button
                        type="button"
                        className="attachment-remove"
                        aria-label="移除图片"
                        title="移除图片"
                        disabled={busy}
                        onClick={() => setAttachment(null)}
                      >
                        <X size={13} />
                      </button>
                    </div>
                  )}
                  {!!files.length && (
                    <ChatFiles
                      files={files}
                      chatId={id!}
                      disabled={busy || fileBusy}
                      onRemove={(fileId) =>
                        setFiles((old) => old.filter((file) => file.id !== fileId))
                      }
                    />
                  )}
                </section>
              )}
              <div className="composer-bottom-tools">
                <AttachmentMenu
                  key={id}
                  chatId={id!}
                  files={files}
                  onFiles={setFiles}
                  onImage={(file) => attachmentTools.current?.image(file)}
                  onDraw={() => attachmentTools.current?.draw()}
                  onBusy={setFileBusy}
                  handler={addFiles}
                  disabled={!user || busy || mediaBusy || unavailable || c?.status === 'OFFLINE'}
                />
                <span>添加图片或文件</span>
              </div>
              <textarea
                aria-label="发送消息"
                onPaste={(e) => {
                  const images = Array.from(e.clipboardData.files).filter((file) =>
                    file.type.startsWith('image/'),
                  );
                  if (images.length) {
                    e.preventDefault();
                    void addFiles.current?.(images);
                  }
                }}
                value={msg}
                onChange={(e) => M(e.target.value)}
                placeholder={'想和 ' + name + ' 聊些什么？'}
                disabled={unavailable || c?.status === 'OFFLINE'}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                    e.preventDefault();
                    void send();
                  }
                }}
              />
              <button
                aria-label="发送消息"
                disabled={
                  busy ||
                  mediaBusy ||
                  fileBusy ||
                  unavailable ||
                  c?.status === 'OFFLINE' ||
                  (!msg.trim() && !attachment && !files.length)
                }
                onClick={send}
              >
                {busy ? <LoaderCircle className="spin" size={21} /> : <Send size={21} />}
              </button>
            </div>
            {error && (
              <p className="auth-error" role="status">
                {error}
                {user && error.includes('星币余额不足') && (
                  <Link to="/wallet"> 前往星币钱包 →</Link>
                )}
              </p>
            )}
            <div className="composer-note">
              <span>Enter 发送 · Shift + Enter 换行</span>
              <p>AI 生成内容仅供参考 · {price} 星币 / 条</p>
            </div>
          </footer>
        </section>
        <aside className="conversation-character">
          <Avatar src={c?.avatar_url} name={name} large />
          <p className="eyebrow">ABOUT THE CHARACTER</p>
          <h2>{name}</h2>
          <p className="character-description">{c?.summary}</p>
          <p className="character-opening">{c?.opening}</p>
          <div className="role-tags">
            {c?.tags
              ?.split(/[,，\s]+/)
              .filter(Boolean)
              .map((t, i) => (
                <span key={i}># {t}</span>
              ))}
          </div>
          <div className="role-cost">
            <Coins size={18} />
            {price} 星币 / 条
          </div>
        </aside>
      </div>
    </main>
  );
}
