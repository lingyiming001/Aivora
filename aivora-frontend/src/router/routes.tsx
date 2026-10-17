import ConfiguredMenuPage from '@/pages/system/permissions/ConfiguredMenuPage';
import AdminAccount from '@/pages/system/account';
import { Navigate, Route, Routes } from 'react-router-dom';
import ModelAdmin from '@/pages/system/models/index';
import AuthPage from '@/pages/login/index';
import ChatPage from '@/pages/chat/index';
import ProfilePage from '@/pages/profile/index';
import WalletPage from '@/pages/wallet/index';
import MessagesPage from '@/pages/messages/index';
import FavoritesPage from '@/pages/favorites/index';
import { AdminData } from '@/pages/system/data/index';
import CharacterEditor from '@/pages/characters/editor/index';
import { Conversations } from '@/pages/conversations/index';
import { MyCharacters } from '@/pages/characters/mine/index';
import { CardAdmin } from '@/pages/system/review/index';
import { UsersAdmin } from '@/pages/system/user/index';
import { AdministratorAdmin as AccessAdmin } from '@/pages/system/administrators/index';
import { RolesAdmin } from '@/pages/system/roles/index';
import { PermissionsAdmin } from '@/pages/system/permissions/index';
import { Layout } from '@/layouts/BasicLayout/index';
import { Square } from '@/pages/discovery/index';
import { Detail } from '@/pages/characters/detail/index';
import { Admin } from '@/pages/dashboard/index';
import { Protected } from '@/router/guard';

export function AppRoutes() {
  return (
    <>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Square />} />
          <Route path="/characters/:id" element={<Detail />} />
          <Route
            path="/create"
            element={
              <Protected>
                <CharacterEditor />
              </Protected>
            }
          />
          <Route
            path="/characters/:id/edit"
            element={
              <Protected>
                <CharacterEditor />
              </Protected>
            }
          />
          <Route path="/chats" element={<Conversations />} />
          <Route
            path="/my-characters"
            element={
              <Protected>
                <MyCharacters />
              </Protected>
            }
          />
          <Route
            path="/favorites"
            element={
              <Protected>
                <FavoritesPage />
              </Protected>
            }
          />
          <Route
            path="/profile"
            element={
              <Protected>
                <ProfilePage />
              </Protected>
            }
          />
          <Route
            path="/messages"
            element={
              <Protected>
                <MessagesPage />
              </Protected>
            }
          />
          <Route
            path="/wallet"
            element={
              <Protected>
                <WalletPage />
              </Protected>
            }
          />
          <Route path="/chat/:id" element={<ChatPage />} />
        </Route>
        <Route path="/login" element={<AuthPage key="user" />} />
        <Route path="/admin/login" element={<AuthPage key="admin" admin />} />
        <Route
          element={
            <Protected admin>
              <Layout admin />
            </Protected>
          }
        >
          <Route path="/admin" element={<Admin />} />
          <Route path="/admin/account" element={<AdminAccount />} />
          <Route path="/admin/users" element={<UsersAdmin />} />
          <Route path="/admin/review" element={<CardAdmin />} />
          <Route path="/admin/audit" element={<Admin />} />
          <Route path="/admin/access" element={<AccessAdmin />} />
          <Route path="/admin/roles" element={<RolesAdmin />} />
          <Route path="/admin/permissions" element={<PermissionsAdmin />} />
          <Route path="/admin/models" element={<ModelAdmin />} />
          <Route path="/admin/:menuKey" element={<ConfiguredMenuPage />} />
          {['orders', 'ledger', 'reconciliation', 'status'].map((path) => (
            <Route key={path} path={'/admin/' + path} element={<AdminData key={path} />} />
          ))}
        </Route>
        <Route path="*" element={<Navigate replace to="/" />} />
      </Routes>
    </>
  );
}
