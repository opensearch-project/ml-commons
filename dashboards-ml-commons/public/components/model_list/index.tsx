import React, { useEffect, useState, useCallback } from 'react';
import { EuiPageHeader, EuiButton, EuiSpacer } from '@elastic/eui';

import { ModelSearchItem } from '../../apis/model';
import { APIProvider } from '../../apis/api_provider';

import { ModelTable } from './model_table';

export function ModelList() {
  const [models, setModels] = useState<ModelSearchItem[]>([]);
  const [pagination, setCurrentPageAndPageSize] = useState({
    currentPage: 1,
    pageSize: 15,
    totalRecords: undefined as number | undefined,
    totalPages: undefined as number | undefined,
  });

  const handlePaginationChange = useCallback(
    (pagination: { currentPage: number; pageSize: number }) => {
      setCurrentPageAndPageSize((previousValue) => {
        if (
          previousValue.currentPage === pagination.currentPage &&
          previousValue.pageSize === pagination.pageSize
        ) {
          return previousValue;
        }
        return {
          ...previousValue,
          ...pagination,
        };
      });
    },
    []
  );

  const reloadModels = useCallback(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 1000);
    });
    const payload = await APIProvider.getAPI('model').search({
      currentPage: pagination.currentPage,
      pageSize: pagination.pageSize,
    });
    setModels(payload.data);
    setCurrentPageAndPageSize(payload.pagination);
  }, [pagination.currentPage, pagination.pageSize]);

  useEffect(() => {
    APIProvider.getAPI('model')
      .search({ currentPage: pagination.currentPage, pageSize: pagination.pageSize })
      .then((payload) => {
        setModels(payload.data);
        setCurrentPageAndPageSize(payload.pagination);
      });
  }, [pagination.currentPage, pagination.pageSize]);

  return (
    <>
      <EuiPageHeader
        pageTitle="Models"
        rightSideItems={[<EuiButton fill>Train new model</EuiButton>]}
        bottomBorder
      />
      <EuiSpacer />
      <ModelTable
        models={models}
        pagination={pagination}
        onPaginationChange={handlePaginationChange}
        onModelDeleted={reloadModels}
      />
    </>
  );
}
