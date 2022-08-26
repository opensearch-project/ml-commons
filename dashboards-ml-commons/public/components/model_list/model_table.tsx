import React, { useMemo, useCallback, useRef } from 'react';
import { EuiBasicTable } from '@elastic/eui';
import { DefaultItemIconButtonAction } from '@elastic/eui/src/components/basic_table/action_types';

import { ModelSearchItem } from '../../apis/model';
import { APIProvider } from '../../apis/api_provider';

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
  const onModelDeletedRef = useRef(onModelDeleted);
  onModelDeletedRef.current = onModelDeleted;

  const handleModelDelete = useCallback((item: ModelSearchItem) => {
    APIProvider.getAPI('model')
      .delete(item.id)
      .then(() => {
        onModelDeletedRef.current();
      });
  }, []);

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
        name: 'Actions',
        actions: [
          {
            name: 'Delete',
            description: 'Delete this model',
            icon: 'trash',
            type: 'icon',
            color: 'danger',
            onClick: handleModelDelete,
          } as DefaultItemIconButtonAction<ModelSearchItem>,
        ],
      },
    ],
    [handleModelDelete]
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
    <EuiBasicTable
      columns={columns}
      items={models}
      pagination={pagination}
      onChange={handleChange}
    />
  );
}
