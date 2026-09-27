import { useEffect, useState } from 'react';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { Grid, GridColumn } from '@vaadin/react-components';
import { DocumentService } from 'Frontend/generated/endpoints';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';

export const config: ViewConfig = {
  title: 'Documents',
  menu: { order: 1 },
};

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentsView() {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);

  useEffect(() => {
    DocumentService.list().then(setDocuments);
  }, []);

  return (
    <main style={{ padding: '1rem', height: '100%', boxSizing: 'border-box' }}>
      <Grid items={documents} allRowsVisible={documents.length < 20}>
        <GridColumn path="fileName" header="Name" flexGrow={3} />
        <GridColumn header="Size" autoWidth>
          {({ item }: { item: DocumentDto }) => formatSize(item.sizeBytes)}
        </GridColumn>
        <GridColumn header="Uploaded" autoWidth>
          {({ item }: { item: DocumentDto }) => new Date(item.createdAt).toLocaleString()}
        </GridColumn>
        <GridColumn path="status" header="Status" autoWidth />
      </Grid>
      {documents.length === 0 && <p>No documents yet.</p>}
    </main>
  );
}
