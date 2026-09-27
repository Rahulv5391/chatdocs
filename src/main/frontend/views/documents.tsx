import { useCallback, useEffect, useState } from 'react';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { EndpointError } from '@vaadin/hilla-frontend';
import {
  Button,
  ConfirmDialog,
  Grid,
  GridColumn,
  Notification,
  Upload,
  type UploadRequestEvent,
} from '@vaadin/react-components';
import { DocumentService } from 'Frontend/generated/endpoints';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';
import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';
import StatusBadge from 'Frontend/components/StatusBadge';

export const config: ViewConfig = {
  title: 'Documents',
  menu: { order: 1 },
};

const MAX_FILE_SIZE = 20 * 1024 * 1024;
const POLL_INTERVAL_MS = 3000;

function isInProgress(document: DocumentDto) {
  return document.status === DocumentStatus.UPLOADED || document.status === DocumentStatus.PROCESSING;
}

function details(document: DocumentDto) {
  if (document.status === DocumentStatus.READY) return `${document.chunkCount} chunks`;
  if (document.status === DocumentStatus.FAILED) return document.errorMessage ?? 'Unknown error';
  return '';
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function showError(message: string) {
  Notification.show(message, { theme: 'error', position: 'bottom-end', duration: 5000 });
}

export default function DocumentsView() {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);
  const [toDelete, setToDelete] = useState<DocumentDto>();

  const refresh = useCallback(() => DocumentService.list().then(setDocuments), []);

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
      file.error = e instanceof EndpointError ? e.message : 'Upload failed.';
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
      showError(e instanceof EndpointError ? e.message : 'Reprocess failed.');
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
      Notification.show(`Deleted ${doc.fileName}`, { theme: 'success', position: 'bottom-end' });
    } catch (e) {
      showError(e instanceof EndpointError ? e.message : 'Delete failed.');
    } finally {
      await refresh();
    }
  }

  return (
    <main style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <Upload
        accept=".pdf,.docx,.txt,.md"
        maxFileSize={MAX_FILE_SIZE}
        onUploadRequest={handleUploadRequest}
        onFileReject={(e) => showError(`${e.detail.file.name}: ${e.detail.error}`)}
      />
      <Grid items={documents} allRowsVisible={documents.length < 20}>
        <GridColumn path="fileName" header="Name" flexGrow={3} />
        <GridColumn header="Size" autoWidth>
          {({ item }: { item: DocumentDto }) => formatSize(item.sizeBytes)}
        </GridColumn>
        <GridColumn header="Uploaded" autoWidth>
          {({ item }: { item: DocumentDto }) => new Date(item.createdAt).toLocaleString()}
        </GridColumn>
        <GridColumn header="Status" autoWidth>
          {({ item }: { item: DocumentDto }) => <StatusBadge status={item.status} />}
        </GridColumn>
        <GridColumn header="Details" flexGrow={2}>
          {({ item }: { item: DocumentDto }) => (
            <span
              title={details(item)}
              style={{ color: item.status === DocumentStatus.FAILED ? '#b91c1c' : undefined }}
            >
              {details(item)}
            </span>
          )}
        </GridColumn>
        <GridColumn autoWidth flexGrow={0}>
          {({ item }: { item: DocumentDto }) => (
            <div style={{ display: 'flex', gap: '0.25rem' }}>
              {item.status === DocumentStatus.FAILED && (
                <Button theme="tertiary small" onClick={() => reprocess(item)}>
                  Reprocess
                </Button>
              )}
              <Button theme="error tertiary small" onClick={() => setToDelete(item)}>
                Delete
              </Button>
            </div>
          )}
        </GridColumn>
      </Grid>
      {documents.length === 0 && <p>No documents yet.</p>}
      <ConfirmDialog
        opened={!!toDelete}
        header="Delete document?"
        cancelButtonVisible
        confirmText="Delete"
        confirmTheme="error primary"
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(undefined)}
      >
        {toDelete && `"${toDelete.fileName}" will be permanently deleted.`}
      </ConfirmDialog>
    </main>
  );
}
