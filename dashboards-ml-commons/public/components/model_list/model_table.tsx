import React, { useMemo, useCallback, useRef } from 'react';
import { generatePath, useHistory } from 'react-router-dom';
import { CustomItemAction, EuiBasicTable, EuiButtonIcon } from '@elastic/eui';

import { ModelSearchItem } from '../../apis/model';
import { routerPaths } from '../../../common/router_paths';
import { renderTime } from '../../utils';

export function ModelTable(props: {
  models: ModelSearchItem[];
  pagination: {
    currentPage: number;
    pageSize: number;
    totalRecords: number | undefined;
  };
  onPaginationChange: (pagination: { currentPage: number; pageSize: number }) => void;
  onModelDelete: (id: string) => void;
}) {
  const { models, onPaginationChange, onModelDelete } = props;
  const history = useHistory();
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
        width: '500px',
      },
      {
        field: 'trainTime',
        name: 'Train Time',
        render: renderTime,
      },
      {
        name: 'Actions',
        actions: [
          {
            render: ({ id }) => (
              <EuiButtonIcon
                iconType="trash"
                color="danger"
                onClick={(e: { stopPropagation: () => void }) => {
                  e.stopPropagation();
                  onModelDelete(id);
                }}
              />
            ),
          } as CustomItemAction<ModelSearchItem>,
        ],
      },
    ],
    [onModelDelete]
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

  const rowProps = useCallback(
    ({ id }) => ({
      onClick: () => {
        history.push(generatePath(routerPaths.modelDetail, { id }));
      },
    }),
    [history]
  );

  return (
    <EuiBasicTable<ModelSearchItem>
      columns={columns}
      items={models}
      pagination={pagination}
      onChange={handleChange}
      rowProps={rowProps}
    />
  );
}
