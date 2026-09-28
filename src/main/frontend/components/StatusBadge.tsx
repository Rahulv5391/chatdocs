import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';

const LABELS: Record<DocumentStatus, string> = {
  [DocumentStatus.UPLOADED]: 'Queued',
  [DocumentStatus.PROCESSING]: 'Processing',
  [DocumentStatus.READY]: 'Ready',
  [DocumentStatus.FAILED]: 'Failed',
};

export default function StatusBadge({ status }: { status: DocumentStatus }) {
  return <span className={`badge ${status.toLowerCase()}`}>{LABELS[status]}</span>;
}
