import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Modal, Button } from 'antd';
import { FileText, X, Download } from 'lucide-react';
import { api } from '@/services/request';
import { type FileAttachment } from '../types';
import { explain } from './VoiceTools';
export function ChatFiles({
  files,
  chatId,
  onRemove,
  disabled = false,
}: {
  files: FileAttachment[];
  chatId: string;
  onRemove?: (id: string) => void;
  disabled?: boolean;
}) {
  const [selected, S] = useState<FileAttachment | null>(null);
  return (
    <>
      <div className="chat-files">
        {files.map((file) => (
          <div className="chat-file-card" key={file.id}>
            <button
              type="button"
              className="chat-file-open"
              onClick={() => S(file)}
              aria-label={'查看文件 ' + file.name}
            >
              <FileText size={24} />
              <span>
                <strong>{file.name}</strong>
                <small>
                  {(file.byte_size / 1024).toFixed(0)} KB ·{' '}
                  {file.truncated ? '部分内容已解析' : '已解析'}
                </small>
              </span>
            </button>
            {onRemove && (
              <button
                type="button"
                className="attachment-remove"
                title="移除文件"
                aria-label={'移除文件 ' + file.name}
                disabled={disabled}
                onClick={() => onRemove(file.id)}
              >
                <X size={14} />
              </button>
            )}
          </div>
        ))}
      </div>
      {selected && (
        <FilePreview key={selected.id} file={selected} chatId={chatId} close={() => S(null)} />
      )}
    </>
  );
}
function FilePreview({
  file,
  chatId,
  close,
}: {
  file: FileAttachment;
  chatId: string;
  close: () => void;
}) {
  const [busy, B] = useState(false),
    [error, E] = useState('');
  const q = useQuery({
    queryKey: ['chat-file', chatId, file.id],
    queryFn: async () =>
      (await api.get<FileAttachment & { text: string }>('/chats/' + chatId + '/files/' + file.id))
        .data,
    retry: false,
  });
  async function download() {
    B(true);
    E('');
    let url = '';
    try {
      const { data } = await api.get<Blob>('/chats/' + chatId + '/files/' + file.id + '/download', {
        responseType: 'blob',
      });
      url = URL.createObjectURL(data);
      const link = document.createElement('a');
      link.href = url;
      link.download = file.name;
      link.click();
    } catch (e) {
      E(explain(e));
    } finally {
      if (url) setTimeout(() => URL.revokeObjectURL(url), 10000);
      B(false);
    }
  }
  return (
    <Modal
      open
      centered
      width={760}
      title={file.name}
      onCancel={close}
      footer={
        <>
          <Button onClick={close}>关闭</Button>
          <Button loading={busy} icon={<Download size={15} />} onClick={() => void download()}>
            下载原文件
          </Button>
        </>
      }
    >
      <p className="muted">
        {file.truncated ? '文档较长，当前只解析了部分内容。' : '以下为角色可读取的文件正文。'}
      </p>
      {q.isPending ? (
        <p>正在读取…</p>
      ) : q.isError ? (
        <p role="alert">
          无法读取文件 <Button onClick={() => q.refetch()}>重试</Button>
        </p>
      ) : (
        <pre className="chat-file-preview">{q.data?.text}</pre>
      )}
      {error && <p role="alert">{error}</p>}
    </Modal>
  );
}
