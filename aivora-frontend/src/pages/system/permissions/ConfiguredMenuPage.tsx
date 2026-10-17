import { useParams } from 'react-router-dom';
import { useAdminSession } from '@/hooks/useAdminSession';
export default function ConfiguredMenuPage() {
  const { menuKey } = useParams();
  const session = useAdminSession();
  const menu = session.data?.menuCatalog?.find((m) => m.menu_key === menuKey);
  return (
    <main className="admin-main">
      <p className="eyebrow">WORKSPACE</p>
      <h1>{menu?.name ?? '业务菜单'}</h1>
      <p className="muted">{menu?.description || '此菜单已注册，业务功能尚待接入。'}</p>
      <section className="panel">
        <h2>页面待接入</h2>
        <p className="muted">菜单授权已经生效；完成对应业务页面与接口后，即可在这里使用。</p>
      </section>
    </main>
  );
}
