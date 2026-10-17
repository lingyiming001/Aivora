import { useEffect, useRef, useState } from 'react';
import { Button, Input, Modal } from 'antd';
import { Download, Pencil, MessageSquarePlus, Sparkles, Maximize2 } from 'lucide-react';
import { api } from '@/services/request';
import { type ChatAttachment } from '../types';
import { explain } from '@/utils/actionError';
export default function GeneratedImageCard({
  picture,
  chatId,
  disabled,
  canChat,
  onAdd,
  onEdited,
  onBusy,
}: {
  picture: ChatAttachment;
  chatId: string;
  disabled: boolean;
  canChat: boolean;
  onAdd: (picture: ChatAttachment) => void;
  onEdited: (picture: ChatAttachment) => void;
  onBusy: (busy: boolean) => void;
}) {
  const [editing, ED] = useState(false),
    [preview, PV] = useState(false),
    [prompt, P] = useState(''),
    [busy, B] = useState(false),
    [downloading, DL] = useState(false),
    [error, E] = useState('');
  const mounted = useRef(true),
    request = useRef<AbortController | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      request.current?.abort();
    };
  }, []);
  async function save() {
    if (busy || disabled || !prompt.trim()) return;
    B(true);
    onBusy(true);
    E('');
    const controller = new AbortController();
    request.current = controller;
    try {
      const { data } = await api.post<ChatAttachment>(
        '/chats/' + chatId + '/media/edit',
        { attachmentId: picture.attachmentId, prompt: prompt.trim() },
        { timeout: 280000, signal: controller.signal },
      );
      if (mounted.current) {
        onEdited(data);
        ED(false);
        P('');
      }
    } catch (e) {
      if (mounted.current) E(explain(e));
    } finally {
      if (mounted.current) {
        B(false);
        onBusy(false);
      }
      request.current = null;
    }
  }
  async function download() {
    if (downloading) return;
    DL(true);
    E('');
    let url = '';
    try {
      const { data, headers } = await api.get<Blob>(
        '/chats/' + chatId + '/media/images/' + picture.attachmentId + '/download',
        { responseType: 'blob' },
      );
      url = URL.createObjectURL(data);
      const link = document.createElement('a');
      link.href = url;
      link.download =
        'Aivora-image.' + (String(headers['content-type']).includes('png') ? 'png' : 'jpg');
      link.click();
    } catch (e) {
      if (mounted.current) E(explain(e));
    } finally {
      if (url) setTimeout(() => URL.revokeObjectURL(url), 10000);
      if (mounted.current) DL(false);
    }
  }
  return (
    <section className="generated-image-card">
      <header>
        <div>
          <span className="generated-image-badge">
            <Sparkles size={15} />
          </span>
          <div>
            <strong>你的创作</strong>
            <small>编辑画面，或带进故事里</small>
          </div>
        </div>
        <button type="button" aria-label="放大预览图片" onClick={() => PV(true)}>
          <Maximize2 size={16} />
        </button>
      </header>
      <button
        type="button"
        className="generated-image-stage"
        aria-label="查看生成图片"
        onClick={() => PV(true)}
      >
        <img src={picture.image} alt="AI生成图片" />
      </button>
      <div className="generated-image-actions">
        <Button
          icon={<Pencil size={15} />}
          disabled={disabled || busy}
          onClick={() => {
            E('');
            ED(true);
          }}
        >
          编辑图片
        </Button>
        <Button icon={<Download size={15} />} loading={downloading} onClick={() => void download()}>
          下载
        </Button>
        <Button
          type="primary"
          icon={<MessageSquarePlus size={15} />}
          disabled={disabled || busy || !canChat}
          onClick={() => onAdd(picture)}
        >
          加入聊天框
        </Button>
      </div>
      {!editing && error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}
      <Modal
        open={preview}
        centered
        width={860}
        title="图片预览"
        footer={
          <>
            <Button
              type="primary"
              disabled={disabled || busy || !canChat}
              onClick={() => {
                onAdd(picture);
                PV(false);
              }}
            >
              加入聊天框
            </Button>
            <Button
              icon={<Download size={15} />}
              loading={downloading}
              onClick={() => void download()}
            >
              下载图片
            </Button>
          </>
        }
        onCancel={() => PV(false)}
      >
        <img className="generated-image-full" src={picture.image} alt="生成图片完整预览" />
      </Modal>
      <Modal
        open={editing}
        centered
        width={680}
        title="编辑图片"
        maskClosable={false}
        closable={!busy}
        keyboard={!busy}
        onCancel={() => !busy && ED(false)}
        footer={
          <>
            <Button disabled={busy} onClick={() => ED(false)}>
              取消
            </Button>
            <Button
              type="primary"
              loading={busy}
              disabled={disabled || !prompt.trim()}
              onClick={() => void save()}
            >
              应用修改
            </Button>
          </>
        }
      >
        <div className="image-edit-layout">
          <img src={picture.image} alt="待编辑图片" />
          <div>
            <label htmlFor="image-edit-prompt">想修改什么？</label>
            <p>例如：保留人物和构图，把背景改为海边日落。</p>
            <Input.TextArea
              id="image-edit-prompt"
              rows={4}
              maxLength={800}
              showCount
              disabled={busy}
              value={prompt}
              onChange={(e) => P(e.target.value)}
              placeholder="描述需要保留和调整的内容"
            />
          </div>
        </div>
        {error && (
          <p role="alert" className="auth-error">
            {error}
          </p>
        )}
      </Modal>
    </section>
  );
}
