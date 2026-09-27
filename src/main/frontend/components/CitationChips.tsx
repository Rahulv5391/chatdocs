import { Popover } from '@vaadin/react-components';
import type Citation from 'Frontend/generated/com/company/chatdocs/dto/Citation';

function label(citation: Citation) {
  return `[${citation.index}] ${citation.fileName}${citation.page ? ` · p.${citation.page}` : ''}`;
}

/** The original file; browsers' PDF viewers open at the page given after #page=. */
function fileUrl(citation: Citation) {
  return `/api/documents/${citation.documentId}/file${citation.page ? `#page=${citation.page}` : ''}`;
}

/**
 * The sources behind one answer. Each chip opens the original file; hovering or focusing it shows the passage.
 * {@code idPrefix} must be unique per message, because the popover finds its chip by element id.
 */
export default function CitationChips({ citations, idPrefix }: { citations: Citation[]; idPrefix: string }) {
  if (citations.length === 0) {
    return null;
  }
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.375rem', marginTop: '0.5rem' }}>
      {citations.map((citation) => {
        const id = `${idPrefix}-cite-${citation.index}`;
        return (
          <span key={citation.index}>
            <a
              id={id}
              href={fileUrl(citation)}
              target="_blank"
              rel="noopener"
              style={{
                display: 'inline-block',
                padding: '0.125rem 0.5rem',
                borderRadius: '999px',
                background: '#e0e7ff',
                color: '#3730a3',
                fontSize: '0.8125rem',
                textDecoration: 'none',
                whiteSpace: 'nowrap',
              }}
            >
              {label(citation)}
            </a>
            <Popover for={id} trigger={['hover', 'focus']} position="top-start">
              <div style={{ maxWidth: '24rem', padding: '0.25rem', fontSize: '0.875rem' }}>
                <strong>{label(citation)}</strong>
                <p style={{ margin: '0.5rem 0 0' }}>{citation.snippet}</p>
              </div>
            </Popover>
          </span>
        );
      })}
    </div>
  );
}
