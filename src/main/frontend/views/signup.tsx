import { type FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { PasswordField, TextField } from '@vaadin/react-components';
import { SignupService } from 'Frontend/generated/endpoints';
import { useAuth } from 'Frontend/auth';
import AuthCard from 'Frontend/components/AuthCard';
import { errorMessage } from 'Frontend/util/notifications';

export const config: ViewConfig = {
  title: 'Sign up',
  skipLayouts: true,
  menu: { exclude: true },
};

// Same rules as SignupService, checked here too so mistakes show while typing.
const USERNAME = /^[a-z0-9][a-z0-9._-]{2,31}$/;
const MIN_PASSWORD_LENGTH = 8;

export default function SignupView() {
  const { state, login } = useAuth();
  const [displayName, setDisplayName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  if (state.user && !submitting) {
    return <Navigate to="/" replace />;
  }

  const normalizedUsername = username.trim().toLowerCase();
  const usernameError =
    username && !USERNAME.test(normalizedUsername) ? '3–32 letters, digits, dots, dashes or underscores' : undefined;
  const passwordError =
    password && password.length < MIN_PASSWORD_LENGTH ? `At least ${MIN_PASSWORD_LENGTH} characters` : undefined;
  const confirmError = confirm && confirm !== password ? "Passwords don't match" : undefined;
  const complete = displayName.trim() && username && password && confirm;
  const valid = complete && !usernameError && !passwordError && !confirmError;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid || submitting) return;
    setSubmitting(true);
    setError(undefined);
    try {
      await SignupService.register(normalizedUsername, displayName, password);
      // Log straight in; the page then redirects to the app.
      const result = await login(normalizedUsername, password, { navigate: () => {} });
      if (result.error) {
        setError('Your account was created, but signing in failed. Please log in.');
      }
    } catch (e) {
      setError(errorMessage(e, 'Could not create the account. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Upload documents and chat with them in minutes."
      footer={
        <>
          Already have an account? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <form onSubmit={submit}>
        {error && <div className="login-error">{error}</div>}
        <TextField
          label="Name"
          autocomplete="name"
          autofocus
          value={displayName}
          maxlength={100}
          onValueChanged={(e) => setDisplayName(e.detail.value)}
        />
        <TextField
          label="Username"
          autocomplete="username"
          value={username}
          maxlength={32}
          invalid={!!usernameError}
          errorMessage={usernameError}
          onValueChanged={(e) => setUsername(e.detail.value)}
        />
        <PasswordField
          label="Password"
          autocomplete="new-password"
          value={password}
          invalid={!!passwordError}
          errorMessage={passwordError}
          helperText={passwordError ? undefined : `At least ${MIN_PASSWORD_LENGTH} characters`}
          onValueChanged={(e) => setPassword(e.detail.value)}
        />
        <PasswordField
          label="Confirm password"
          autocomplete="new-password"
          value={confirm}
          invalid={!!confirmError}
          errorMessage={confirmError}
          onValueChanged={(e) => setConfirm(e.detail.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit(e)}
        />
        <button type="submit" className="btn primary" disabled={!valid || submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
    </AuthCard>
  );
}
