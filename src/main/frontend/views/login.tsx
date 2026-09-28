import { type FormEvent, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { PasswordField, TextField } from '@vaadin/react-components';
import { useAuth } from 'Frontend/auth';
import AuthCard from 'Frontend/components/AuthCard';

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
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [hasError, setHasError] = useState(false);
  const [redirectTo, setRedirectTo] = useState<string>();
  // login() marks the user as logged in before it returns the redirect URL; wait for both.
  const [loggingIn, setLoggingIn] = useState(false);

  if (state.user && !loggingIn) {
    // The page the user originally asked for: saved by Spring Security (server redirect) or by the router.
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;
    return <Navigate to={redirectTo ?? from ?? '/'} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!username || !password) return;
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
  }

  return (
    <AuthCard
      title="Welcome to DocChat"
      subtitle="Chat with your documents. Sign in to continue."
      footer={
        <>
          New here? <Link to="/signup">Create an account</Link>
        </>
      }
    >
      <form onSubmit={submit}>
        {hasError && <div className="login-error">Incorrect username or password.</div>}
        <TextField
          label="Username"
          autocomplete="username"
          autofocus
          value={username}
          onValueChanged={(e) => setUsername(e.detail.value)}
        />
        <PasswordField
          label="Password"
          autocomplete="current-password"
          value={password}
          onValueChanged={(e) => setPassword(e.detail.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit(e)}
        />
        <button type="submit" className="btn primary" disabled={loggingIn || !username || !password}>
          {loggingIn ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </AuthCard>
  );
}
