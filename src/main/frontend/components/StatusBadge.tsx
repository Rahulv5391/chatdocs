import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';

const COLORS: Record<DocumentStatus, { color: string; background: string }> = {
  [DocumentStatus.UPLOADED]: { color: '#475569', background: '#e2e8f0' },
  [DocumentStatus.PROCESSING]: { color: '#1d4ed8', background: '#dbeafe' },
  [DocumentStatus.READY]: { color: '#15803d', background: '#dcfce7' },
  [DocumentStatus.FAILED]: { color: '#b91c1c', background: '#fee2e2' },
};

const LABELS: Record<DocumentStatus, string> = {
  [DocumentStatus.UPLOADED]: 'Queued',
  [DocumentStatus.PROCESSING]: 'Processing…',
  [DocumentStatus.READY]: 'Ready',
  [DocumentStatus.FAILED]: 'Failed',
};

export default function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <span
      style={{
        ...COLORS[status],
        padding: '0.125rem 0.5rem',
        borderRadius: '999px',
        fontSize: '0.8125rem',
        fontWeight: 500,
        whiteSpace: 'nowrap',
      }}
    >
      {LABELS[status]}
    </span>
  );
}
