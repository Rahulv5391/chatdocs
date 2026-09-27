import { useEffect, useState } from 'react';
import { MultiSelectComboBox } from '@vaadin/react-components';
import { DocumentService } from 'Frontend/generated/endpoints';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';
import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';

/**
 * Picks which documents a new chat may use. Choosing none means "all my documents".
 * Only READY documents are offered, since the others have nothing searchable yet.
 */
export default function DocumentScopePicker({ onChange }: { onChange: (documentIds: string[]) => void }) {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);
  const [selected, setSelected] = useState<DocumentDto[]>([]);

  useEffect(() => {
    DocumentService.list().then((all) => setDocuments(all.filter((d) => d.status === DocumentStatus.READY)));
  }, []);

  return (
    <MultiSelectComboBox
      label="Documents"
      helperText={selected.length === 0 ? 'None selected: the chat uses all your documents.' : undefined}
      placeholder="All documents"
      items={documents}
      itemLabelPath="fileName"
      itemIdPath="id"
      selectedItems={selected}
      clearButtonVisible
      style={{ width: '100%' }}
      onSelectedItemsChanged={(e) => {
        const items = e.detail.value as DocumentDto[];
        setSelected(items);
        onChange(items.map((d) => d.id));
      }}
    />
  );
}
