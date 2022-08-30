import React, { useEffect, useState, useCallback } from 'react';
import { EuiPageHeader, EuiSpacer, EuiPanel } from '@elastic/eui';

import { TaskSearchItem } from '../../apis/task';
import { APIProvider } from '../../apis/api_provider';
import { TaskTable } from './task_table';

export function TaskList() {
  const [tasks, setTasks] = useState<TaskSearchItem[]>([]);
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

  const handleTaskDeleted = useCallback(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 1000);
    });
    const payload = await APIProvider.getAPI('task').search({
      currentPage: pagination.currentPage,
      pageSize: pagination.pageSize,
    });
    setTasks(payload.data);
    setCurrentPageAndPageSize(payload.pagination);
  }, [pagination.currentPage, pagination.pageSize]);

  useEffect(() => {
    APIProvider.getAPI('task')
      .search({ currentPage: pagination.currentPage, pageSize: pagination.pageSize })
      .then((payload) => {
        setTasks(payload.data);
        setCurrentPageAndPageSize(payload.pagination);
      });
  }, [pagination.currentPage, pagination.pageSize]);

  return (
    <EuiPanel>
      <EuiPageHeader pageTitle="Tasks" bottomBorder />
      <EuiSpacer />
      <TaskTable
        tasks={tasks}
        pagination={pagination}
        onTaskDeleted={handleTaskDeleted}
        onPaginationChange={handlePaginationChange}
      />
    </EuiPanel>
  );
}
