import React, { useMemo, useCallback, useRef } from 'react';
import { EuiBasicTable } from '@elastic/eui';

import { ModelSearchItem } from '../../apis/model';
import { ModelDeleteButton } from './model_delete_button';

export function ModelTable(props: {
  models: ModelSearchItem[];
  pagination: {
    currentPage: number;
    pageSize: number;
    totalRecords: number | undefined;
  };
  onPaginationChange: (pagination: { currentPage: number; pageSize: number }) => void;
  onModelDeleted: () => void;
}) {
  const { models, onPaginationChange, onModelDeleted } = props;
  const onPaginationChangeRef = useRef(onPaginationChange);
  onPaginationChangeRef.current = onPaginationChange;

  const columns = useMemo(
    () => [
      {
        field: 'id',
        name: 'ID',
      },
      {
        field: 'name',
        name: 'Name',
      },
      {
        field: 'algorithm',
        name: 'Algorithm',
      },
      {
        field: 'context',
        name: 'Context',
      },
      {
        field: 'id',
        name: 'Actions',
        render: (id: string) => <ModelDeleteButton key={id} id={id} onDeleted={onModelDeleted} />,
      },
    ],
    [onModelDeleted]
  );

  const pagination = useMemo(
    () => ({
      pageIndex: props.pagination.currentPage - 1,
      pageSize: props.pagination.pageSize,
      totalItemCount: props.pagination.totalRecords || 0,
      pageSizeOptions: [15, 30, 50, 100],
      showPerPageOptions: true,
    }),
    [props.pagination]
  );

  const handleChange = useCallback(
    ({ page }) => {
      if (page) {
        onPaginationChangeRef.current({ currentPage: page.index + 1, pageSize: page.size });
      }
    },
    [onPaginationChangeRef.current]
  );

  return (
    <EuiBasicTable<ModelSearchItem>
      columns={columns}
      items={models}
      pagination={pagination}
      onChange={handleChange}
    />
  );
}
