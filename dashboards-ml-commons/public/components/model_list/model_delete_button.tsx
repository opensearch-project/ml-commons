import React, { useCallback, useState } from 'react';
import { EuiButton } from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';
import { usePollingUntil } from '../../hooks/use_polling_until';

export const ModelDeleteButton = ({ id, onDeleted }: { id: string; onDeleted: () => void }) => {
  const [isDeleting, setIsDeleting] = useState(false);
  const { start: startPolling } = usePollingUntil({
    continueChecker: () =>
      APIProvider.getAPI('model')
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

  const handleClick = useCallback(
    async (e) => {
      e.stopPropagation();
      setIsDeleting(true);
      await APIProvider.getAPI('model').delete(id);
      startPolling();
    },
    [id, onDeleted, startPolling]
  );

  return (
    <EuiButton isLoading={isDeleting} isDisabled={isDeleting} onClick={handleClick}>
      Delete
    </EuiButton>
  );
};
