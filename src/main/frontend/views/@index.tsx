import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { useAuth } from 'Frontend/auth';

export const config: ViewConfig = {
  title: 'Home',
  menu: { order: 0 },
};

export default function IndexView() {
  const { state } = useAuth();

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Chat with my docs</h1>
      <p>Welcome, {state.user?.displayName}.</p>
    </main>
  );
}
