import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  title: 'Documents',
  menu: { order: 1 },
};

// Placeholder until Phase 5 adds the documents grid.
export default function DocumentsView() {
  return (
    <main style={{ padding: '2rem' }}>
      <p>Your documents will appear here.</p>
    </main>
  );
}
