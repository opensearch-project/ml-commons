import React, { useCallback, useState } from 'react';
import { EuiButton } from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';
import { usePollingUntil } from '../../hooks/use_polling_until';

export const TaskDeleteButton = ({ id, onDeleted }: { id: string; onDeleted: () => void }) => {
  const [isDeleting, setIsDeleting] = useState(false);
  const { start: startPolling } = usePollingUntil({
    continueChecker: () =>
      APIProvider.getAPI('task')
        .search({
          ids: [id],
          pageSize: 1,
          currentPage: 1,
        })
        .then(({ pagination }) => pagination.totalRecords === 1),
    onGiveUp: () => {
      setIsDeleting(false);
      onDeleted();
    },
    onMaxRetries: () => {
      setIsDeleting(false);
    },
  });

  const handleClick = useCallback(async () => {
    setIsDeleting(true);
    await APIProvider.getAPI('task').delete(id);
    startPolling();
  }, [id, onDeleted, startPolling]);

  return (
    <EuiButton isLoading={isDeleting} isDisabled={isDeleting} onClick={handleClick}>
      Delete
    </EuiButton>
  );
};
