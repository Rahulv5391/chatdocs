import { Suspense, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useViewConfig } from '@vaadin/hilla-file-router/runtime.js';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { AppLayout, type AppLayoutElement, ConfirmDialog, DrawerToggle, Tooltip } from '@vaadin/react-components';
import { ChatService } from 'Frontend/generated/endpoints';
import type ChatSessionDto from 'Frontend/generated/com/company/chatdocs/dto/ChatSessionDto';
import { useAuth } from 'Frontend/auth';
import { ChatSessionsProvider, useChatSessions } from 'Frontend/components/ChatSessions';
import { FileIcon, LogoutIcon, PlusIcon, SparkIcon, TrashIcon } from 'Frontend/components/Icons';
import { errorMessage, showError } from 'Frontend/util/notifications';

export const config: ViewConfig = {
  loginRequired: true,
};

export default function MainLayout() {
  return (
    <ChatSessionsProvider>
      <Shell />
    </ChatSessionsProvider>
  );
}

function Shell() {
  const location = useLocation();
  const viewTitle = useViewConfig()?.title;
  const [overlay, setOverlay] = useState(false);
  const [drawerOpened, setDrawerOpened] = useState(true);
  const layout = useRef<AppLayoutElement>(null);

  // overlay-changed may fire before React listens, so read the initial mode from the element.
  useEffect(() => {
    setOverlay(!!layout.current?.overlay);
  }, []);

  // On small screens the drawer floats over the page; close it after navigating.
  useEffect(() => {
    if (overlay) {
      setDrawerOpened(false);
    }
  }, [location.pathname, overlay]);

  return (
    <AppLayout
      ref={layout}
      primarySection="drawer"
      drawerOpened={drawerOpened}
      onDrawerOpenedChanged={(e) => setDrawerOpened(e.detail.value)}
      onOverlayChanged={(e) => setOverlay(e.detail.value)}
    >
      <Sidebar slot="drawer" />
      {overlay && (
        <div slot="navbar" className="mobile-bar">
          <DrawerToggle aria-label="Menu" />
          <span>{viewTitle}</span>
        </div>
      )}
      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}

function Sidebar({ slot }: { slot: string }) {
  const { state, logout } = useAuth();
  const { sessions, refresh } = useChatSessions();
  const navigate = useNavigate();
  const location = useLocation();
  const [toDelete, setToDelete] = useState<ChatSessionDto>();
  const displayName = state.user?.displayName ?? '';

  async function confirmDelete() {
    if (!toDelete) return;
    const session = toDelete;
    setToDelete(undefined);
    try {
      await ChatService.deleteSession(session.id);
      if (location.pathname === `/chat/${session.id}`) {
        navigate('/chat');
      }
    } catch (e) {
      showError(errorMessage(e, 'Could not delete the chat.'));
    } finally {
      await refresh();
    }
  }

  return (
    <nav slot={slot} className="sidebar">
      <Link to="/chat" className="brand">
        <span className="brand-mark">
          <SparkIcon size={17} />
        </span>
        DocChat
      </Link>

      <NavLink to="/chat" end className="nav-item primary">
        <PlusIcon size={17} />
        New chat
      </NavLink>
      <NavLink to="/documents" className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
        <FileIcon size={17} />
        Documents
      </NavLink>

      <div className="sidebar-section">Recent chats</div>
      <div className="session-list">
        {sessions.length === 0 && <div className="sidebar-empty">Your chats will appear here.</div>}
        {sessions.map((session) => {
          const active = location.pathname === `/chat/${session.id}`;
          return (
            <div key={session.id} className={`session-item${active ? ' active' : ''}`}>
              <Link to={`/chat/${session.id}`} title={session.title}>
                {session.title}
              </Link>
              <button
                className="icon-button danger"
                aria-label={`Delete ${session.title}`}
                onClick={() => setToDelete(session)}
              >
                <TrashIcon size={15} />
              </button>
            </div>
          );
        })}
      </div>

      <div className="user-card">
        <span className="avatar">{displayName.charAt(0).toUpperCase()}</span>
        <span className="name">{displayName}</span>
        <button id="logout-button" className="icon-button" aria-label="Log out" onClick={() => logout()}>
          <LogoutIcon size={17} />
        </button>
        <Tooltip for="logout-button" text="Log out" position="top" />
      </div>

      <ConfirmDialog
        opened={!!toDelete}
        header="Delete chat?"
        cancelButtonVisible
        confirmText="Delete"
        confirmTheme="error primary"
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(undefined)}
      >
        {toDelete && `"${toDelete.title}" and all its messages will be permanently deleted.`}
      </ConfirmDialog>
    </nav>
  );
}
