import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { Dropdown } from 'antd';
import { Plus, FileUp, ImagePlus, WandSparkles, LoaderCircle } from 'lucide-react';
import { api } from '@/services/request';
import { type FileAttachment } from '../types';
import { explain } from './VoiceTools';
export default function AttachmentMenu({
  chatId,
  disabled,
  files,
  onFiles,
  onImage,
  onDraw,
  onBusy,
  handler,
}: {
  chatId: string;
  disabled: boolean;
  files: FileAttachment[];
  onFiles: (files: FileAttachment[]) => void;
  onImage: (file: File) => Promise<void> | undefined;
  onDraw: () => void;
  onBusy: (busy: boolean) => void;
  handler: MutableRefObject<((files: File[]) => Promise<void>) | null>;
}) {
  const input = useRef<HTMLInputElement>(null),
    image = useRef<HTMLInputElement>(null),
    mounted = useRef(true),
    working = useRef(false);
  const [busy, B] = useState(false),
    [error, E] = useState('');
  useEffect(() => {
    mounted.current = true;
    handler.current = add;
    return () => {
      mounted.current = false;
      handler.current = null;
    };
  }, [handler]);
  async function add(selected: File[]) {
    if (disabled || working.current || !selected.length) return;
    if (
      selected.filter((f) => f.type.startsWith('image/')).length > 1 ||
      files.length + selected.filter((f) => !f.type.startsWith('image/')).length > 4
    ) {
      E('每条消息最多添加4个文件和1张图片。');
      return;
    }
    working.current = true;
    B(true);
    onBusy(true);
    E('');
    const uploaded = [...files];
    try {
      for (const file of selected) {
        if (file.size > 5 * 1024 * 1024) throw new Error('每个附件最大5MB，请缩小文件后重试。');
        if (file.type.startsWith('image/')) {
          await onImage(file);
          continue;
        }
        if (!/\.(pdf|docx|txt|md|csv|json|log)$/i.test(file.name))
          throw new Error('支持PDF、Word、TXT、Markdown、CSV、JSON和日志文件。');
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result).split(',')[1]);
          reader.onerror = () => reject(new Error('文件读取失败'));
          reader.readAsDataURL(file);
        });
        const response = await api.post<FileAttachment>(
          '/chats/' + chatId + '/files',
          { name: file.name, data },
          { timeout: 60000 },
        );
        if (!mounted.current) return;
        uploaded.push(response.data);
        onFiles([...uploaded]);
      }
    } catch (e) {
      if (mounted.current) E(explain(e));
    } finally {
      working.current = false;
      if (mounted.current) {
        B(false);
        onBusy(false);
        if (input.current) input.current.value = '';
        if (image.current) image.current.value = '';
      }
    }
  }
  handler.current = add;
  return (
    <div className="composer-attachment-menu">
      <Dropdown
        trigger={['click']}
        menu={{
          items: [
            { key: 'image', label: '添加图片', icon: <ImagePlus size={16} /> },
            { key: 'file', label: '添加文件', icon: <FileUp size={16} /> },
            { key: 'draw', label: '生成图片', icon: <WandSparkles size={16} /> },
          ],
          onClick: ({ key }) =>
            key === 'image'
              ? image.current?.click()
              : key === 'file'
                ? input.current?.click()
                : onDraw(),
        }}
      >
        <button
          type="button"
          className="composer-add"
          aria-label="添加附件"
          disabled={disabled || busy}
        >
          {busy ? <LoaderCircle className="spin" size={20} /> : <Plus size={21} />}
        </button>
      </Dropdown>
      <input
        ref={input}
        type="file"
        multiple
        accept=".pdf,.docx,.txt,.md,.csv,.json,.log"
        className="sr-only"
        aria-label="选择聊天文件"
        onChange={(e) => void add(Array.from(e.target.files ?? []))}
      />
      <input
        ref={image}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="sr-only"
        aria-label="添加聊天图片"
        onChange={(e) => void add(Array.from(e.target.files ?? []))}
      />
      {error && (
        <small role="alert" className="attachment-error">
          {error}
        </small>
      )}
    </div>
  );
}
