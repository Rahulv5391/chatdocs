import { Popover } from '@vaadin/react-components';
import type Citation from 'Frontend/generated/com/company/chatdocs/dto/Citation';

function label(citation: Citation) {
  return `${citation.fileName}${citation.page ? ` · p.${citation.page}` : ''}`;
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
    <div className="citations">
      {citations.map((citation) => {
        const id = `${idPrefix}-cite-${citation.index}`;
        return (
          <span key={citation.index}>
            <a id={id} className="citation" href={fileUrl(citation)} target="_blank" rel="noopener">
              <span className="num">{citation.index}</span>
              <span className="name">{label(citation)}</span>
            </a>
            <Popover for={id} trigger={['hover', 'focus']} position="top-start">
              <div className="citation-preview">
                <strong>
                  [{citation.index}] {label(citation)}
                </strong>
                <p>{citation.snippet}</p>
              </div>
            </Popover>
          </span>
        );
      })}
    </div>
  );
}
