import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  title: 'Chat',
  menu: { order: 2 },
};

// Placeholder until Phase 14 adds chat sessions.
export default function ChatIndexView() {
  return (
    <main style={{ padding: '2rem' }}>
      <p>Your chats will appear here.</p>
    </main>
  );
}
