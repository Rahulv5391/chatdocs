import { useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { LoginForm } from '@vaadin/react-components';
import { useAuth } from 'Frontend/auth';

export const config: ViewConfig = {
  title: 'Log in',
  skipLayouts: true,
  menu: { exclude: true },
};

export default function LoginView() {
  const { state, login } = useAuth();
  const location = useLocation();
  const [hasError, setHasError] = useState(false);

  if (state.user) {
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={from ?? '/'} replace />;
  }

  return (
    <main style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '10vh' }}>
      <h1>Chat with my docs</h1>
      <LoginForm
        error={hasError}
        noForgotPassword
        onLogin={async ({ detail: { username, password } }) => {
          const { error } = await login(username, password);
          setHasError(!!error);
        }}
      />
    </main>
  );
}
