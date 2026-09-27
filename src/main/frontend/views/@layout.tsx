import { Suspense } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { createMenuItems, useViewConfig } from '@vaadin/hilla-file-router/runtime.js';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { AppLayout, Button, DrawerToggle, SideNav, SideNavItem } from '@vaadin/react-components';
import { useAuth } from 'Frontend/auth';

export const config: ViewConfig = {
  loginRequired: true,
};

export default function MainLayout() {
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const viewTitle = useViewConfig()?.title;

  return (
    <AppLayout primarySection="drawer">
      <div slot="drawer" style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: '1rem' }}>
        <h2 style={{ fontSize: '1.125rem', margin: '0 0 1rem' }}>Chat with my docs</h2>
        <SideNav onNavigate={({ path }) => path && navigate(path)} location={location}>
          {createMenuItems().map(({ to, title }) => (
            <SideNavItem path={to} key={to}>
              {title}
            </SideNavItem>
          ))}
        </SideNav>
        <footer style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <span>{state.user?.displayName}</span>
          <Button onClick={() => logout()}>Log out</Button>
        </footer>
      </div>

      <DrawerToggle slot="navbar" aria-label="Menu toggle" />
      <h1 slot="navbar" style={{ fontSize: '1.125rem', margin: 0 }}>
        {viewTitle}
      </h1>

      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
