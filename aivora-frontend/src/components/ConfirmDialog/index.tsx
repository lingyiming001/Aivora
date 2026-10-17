import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Modal } from 'antd';
import { Trash2, ShieldCheck, LoaderCircle } from 'lucide-react';
import { useLocation } from 'react-router-dom';

export type ConfirmOptions = {
  title: string;
  description: string;
  note?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
};
type DialogProps = ConfirmOptions & {
  open: boolean;
  loading?: boolean;
  disabled?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
};

export function ConfirmDialog({
  open,
  title,
  description,
  note,
  confirmLabel = '确定',
  cancelLabel = '取消',
  danger = false,
  loading = false,
  disabled = false,
  onConfirm,
  onCancel,
  children,
}: DialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  return (
    <Modal
      open={open}
      centered
      width={460}
      className="av-confirm"
      rootClassName="av-confirm-root"
      title={
        <div className="av-confirm-heading">
          <span className="av-confirm-icon" data-danger={danger}>
            <span aria-hidden="true">
              {danger ? (
                <Trash2 size={23} strokeWidth={1.6} />
              ) : (
                <ShieldCheck size={23} strokeWidth={1.6} />
              )}
            </span>
          </span>
          <span>
            <small>操作确认</small>
            <span className="av-confirm-title">{title}</span>
          </span>
        </div>
      }
      footer={null}
      maskClosable={false}
      closable={!loading}
      keyboard={!loading}
      onCancel={() => !loading && onCancel()}
      afterOpenChange={(visible) => {
        if (visible) cancelRef.current?.focus();
      }}
    >
      <p className="av-confirm-description">{description}</p>
      {note && (
        <p className="av-confirm-note">
          <span aria-hidden="true" />
          {note}
        </p>
      )}
      {children && <div className="av-confirm-extra">{children}</div>}
      <div className="av-confirm-footer">
        <button
          ref={cancelRef}
          type="button"
          className="av-confirm-button av-confirm-cancel"
          disabled={loading}
          onClick={onCancel}
        >
          {cancelLabel}
        </button>
        <button
          type="button"
          className="av-confirm-button av-confirm-primary"
          data-danger={danger}
          disabled={loading || disabled}
          onClick={onConfirm}
        >
          {loading && <LoaderCircle size={16} className="av-confirm-spinner" />}
          {loading ? '处理中…' : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

const ConfirmContext = createContext<((options: ConfirmOptions) => Promise<boolean>) | null>(null);
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [options, setOptions] = useState<ConfirmOptions>({ title: '', description: '' });
  const [open, setOpen] = useState(false);
  const pending = useRef<((accepted: boolean) => void) | null>(null);
  const location = useLocation();
  const finish = useCallback((accepted: boolean) => {
    const resolve = pending.current;
    pending.current = null;
    setOpen(false);
    resolve?.(accepted);
  }, []);
  const confirm = useCallback((next: ConfirmOptions) => {
    if (pending.current) return Promise.resolve(false);
    return new Promise<boolean>((resolve) => {
      pending.current = resolve;
      setOptions(next);
      setOpen(true);
    });
  }, []);
  useEffect(() => {
    finish(false);
  }, [location.pathname, finish]);
  useEffect(
    () => () => {
      pending.current?.(false);
      pending.current = null;
    },
    [],
  );
  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <ConfirmDialog
        {...options}
        open={open}
        onConfirm={() => finish(true)}
        onCancel={() => finish(false)}
      />
    </ConfirmContext.Provider>
  );
}
export function useConfirm() {
  const confirm = useContext(ConfirmContext);
  if (!confirm) throw new Error('useConfirm requires ConfirmProvider');
  return confirm;
}
