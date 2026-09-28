import { useState } from 'react';
import { MultiSelectComboBox } from '@vaadin/react-components';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';

/**
 * Picks which documents a new chat may use. Choosing none means "all my documents".
 * Pass only READY documents, since the others have nothing searchable yet.
 */
export default function DocumentScopePicker({
  documents,
  onChange,
}: {
  documents: DocumentDto[];
  onChange: (documentIds: string[]) => void;
}) {
  const [selected, setSelected] = useState<DocumentDto[]>([]);

  return (
    <MultiSelectComboBox
      className="scope-select"
      aria-label="Documents to use"
      placeholder="All documents"
      items={documents}
      itemLabelPath="fileName"
      itemIdPath="id"
      selectedItems={selected}
      autoExpandHorizontally
      clearButtonVisible
      onSelectedItemsChanged={(e) => {
        const items = e.detail.value as DocumentDto[];
        setSelected(items);
        onChange(items.map((d) => d.id));
      }}
    />
  );
}
