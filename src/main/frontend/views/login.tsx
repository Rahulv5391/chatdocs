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

/** Keeps only the path of a redirect URL, so login can never send the user to another site. */
function samePagePath(url: string) {
  const parsed = new URL(url, document.baseURI);
  return parsed.origin === location.origin && parsed.pathname !== '/login' ? parsed.pathname + parsed.search : undefined;
}

export default function LoginView() {
  const { state, login } = useAuth();
  const location = useLocation();
  const [hasError, setHasError] = useState(false);
  const [redirectTo, setRedirectTo] = useState<string>();
  // login() marks the user as logged in before it returns the redirect URL; wait for both.
  const [loggingIn, setLoggingIn] = useState(false);

  if (state.user && !loggingIn) {
    // The page the user originally asked for: saved by Spring Security (server redirect) or by the router.
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={redirectTo ?? from ?? '/'} replace />;
  }

  return (
    <main style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: '10vh' }}>
      <h1>Chat with my docs</h1>
      <LoginForm
        error={hasError}
        noForgotPassword
        onLogin={async ({ detail: { username, password } }) => {
          setLoggingIn(true);
          try {
            // No page reload: this view decides where to go (Hilla would otherwise reload to "/").
            const { error, redirectUrl } = await login(username, password, { navigate: () => {} });
            setHasError(!!error);
            if (!error && redirectUrl) {
              setRedirectTo(samePagePath(redirectUrl));
            }
          } finally {
            setLoggingIn(false);
          }
        }}
      />
    </main>
  );
}
