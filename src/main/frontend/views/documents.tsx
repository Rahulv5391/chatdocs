import { useCallback, useEffect, useState } from 'react';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { ConfirmDialog, Tooltip, Upload, type UploadRequestEvent } from '@vaadin/react-components';
import { DocumentService } from 'Frontend/generated/endpoints';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';
import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';
import StatusBadge from 'Frontend/components/StatusBadge';
import { FileIcon, RefreshIcon, TrashIcon, UploadIcon } from 'Frontend/components/Icons';
import { errorMessage, showError, showSuccess } from 'Frontend/util/notifications';

export const config: ViewConfig = {
  title: 'Documents',
};

const MAX_FILE_SIZE = 20 * 1024 * 1024;
const POLL_INTERVAL_MS = 3000;

function isInProgress(document: DocumentDto) {
  return document.status === DocumentStatus.UPLOADED || document.status === DocumentStatus.PROCESSING;
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function extension(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() ?? '';
}

function meta(document: DocumentDto) {
  const parts = [
    formatSize(document.sizeBytes),
    new Date(document.createdAt).toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' }),
  ];
  if (document.status === DocumentStatus.READY) {
    parts.push(`${document.chunkCount} ${document.chunkCount === 1 ? 'chunk' : 'chunks'}`);
  }
  return parts.join(' · ');
}

export default function DocumentsView() {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [toDelete, setToDelete] = useState<DocumentDto>();

  const refresh = useCallback(
    () =>
      DocumentService.list().then((all) => {
        setDocuments(all);
        setLoaded(true);
      }),
    [],
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Ingestion runs in the background, so poll while any document is still queued or processing.
  const polling = documents.some(isInProgress);
  useEffect(() => {
    if (!polling) return;
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [polling, refresh]);

  // Send the file through the generated Hilla client instead of the Upload component's own XHR.
  async function handleUploadRequest(event: UploadRequestEvent) {
    event.preventDefault();
    const upload = event.target;
    const file = event.detail.file;
    try {
      await DocumentService.upload(file);
      file.complete = true;
      await refresh();
    } catch (e) {
      file.error = errorMessage(e, 'Upload failed.');
      showError(`${file.name}: ${file.error}`);
    } finally {
      file.uploading = false;
      file.status = '';
      upload.files = [...upload.files];
    }
  }

  async function reprocess(document: DocumentDto) {
    try {
      await DocumentService.reprocess(document.id);
    } catch (e) {
      showError(errorMessage(e, 'Reprocess failed.'));
    } finally {
      await refresh();
    }
  }

  async function confirmDelete() {
    if (!toDelete) return;
    const doc = toDelete;
    setToDelete(undefined);
    try {
      await DocumentService.delete(doc.id);
      showSuccess(`Deleted ${doc.fileName}`);
    } catch (e) {
      showError(errorMessage(e, 'Delete failed.'));
    } finally {
      await refresh();
    }
  }

  const ready = documents.filter((d) => d.status === DocumentStatus.READY);
  const chunks = ready.reduce((sum, d) => sum + d.chunkCount, 0);

  return (
    <main className="page">
      <div className="page-inner">
        <div className="page-header">
          <div>
            <h1>Documents</h1>
            <p>Upload files and the assistant will answer questions from them.</p>
          </div>
          {documents.length > 0 && (
            <div className="stats">
              <span className="stat">
                <strong>{documents.length}</strong> files
              </span>
              <span className="stat">
                <strong>{ready.length}</strong> ready
              </span>
              <span className="stat">
                <strong>{chunks}</strong> chunks indexed
              </span>
            </div>
          )}
        </div>

        <Upload
          className="dropzone"
          accept=".pdf,.docx,.txt,.md"
          maxFileSize={MAX_FILE_SIZE}
          onUploadRequest={handleUploadRequest}
          onFileReject={(e) => showError(`${e.detail.file.name}: ${e.detail.error}`)}
        >
          <span slot="drop-label-icon" />
          <div slot="drop-label" className="drop-content">
            <span className="drop-icon">
              <UploadIcon size={22} />
            </span>
            <span className="drop-title">Drop files here or browse</span>
            <span className="drop-hint">PDF, DOCX, TXT or Markdown · up to 20 MB each</span>
          </div>
        </Upload>

        {loaded && documents.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">
              <FileIcon size={26} />
            </span>
            <h3>No documents yet</h3>
            <p>Upload your first file above. Once it's ready, start a chat and ask questions about it.</p>
          </div>
        ) : (
          <div className="doc-list">
            {documents.map((document, i) => (
              <div className="doc-row" key={document.id} style={{ animationDelay: `${Math.min(i, 10) * 30}ms` }}>
                <span className={`file-icon ${extension(document.fileName)}`}>
                  {extension(document.fileName).toUpperCase()}
                </span>
                <div className="doc-main">
                  <div className="doc-name">
                    <a href={`/api/documents/${document.id}/file`} target="_blank" rel="noopener">
                      {document.fileName}
                    </a>
                  </div>
                  {document.status === DocumentStatus.FAILED ? (
                    <div className="doc-meta error">{document.errorMessage ?? 'Unknown error'}</div>
                  ) : (
                    <div className="doc-meta">{meta(document)}</div>
                  )}
                </div>
                <StatusBadge status={document.status} />
                <div className="doc-actions">
                  {document.status === DocumentStatus.FAILED && (
                    <>
                      <button
                        id={`reprocess-${document.id}`}
                        className="icon-button"
                        aria-label={`Reprocess ${document.fileName}`}
                        onClick={() => reprocess(document)}
                      >
                        <RefreshIcon size={16} />
                      </button>
                      <Tooltip for={`reprocess-${document.id}`} text="Reprocess" position="top" />
                    </>
                  )}
                  <button
                    id={`delete-${document.id}`}
                    className="icon-button danger"
                    aria-label={`Delete ${document.fileName}`}
                    onClick={() => setToDelete(document)}
                  >
                    <TrashIcon size={16} />
                  </button>
                  <Tooltip for={`delete-${document.id}`} text="Delete" position="top" />
                </div>
              </div>
            ))}
          </div>
        )}

        <ConfirmDialog
          opened={!!toDelete}
          header="Delete document?"
          cancelButtonVisible
          confirmText="Delete"
          confirmTheme="error primary"
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(undefined)}
        >
          {toDelete && `"${toDelete.fileName}" and everything learned from it will be permanently deleted.`}
        </ConfirmDialog>
      </div>
    </main>
  );
}
