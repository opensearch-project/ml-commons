import React, { useCallback, useImperativeHandle, useRef, useState } from 'react';
import { EuiConfirmModal } from '@elastic/eui';
import { APIProvider } from '../../apis/api_provider';
import { usePollingUntil } from '../../hooks/use_polling_until';

export class NoIdProvideError {}

export interface ModelConfirmDeleteModalInstance {
  show: (modelId: string) => void;
}

export const ModelConfirmDeleteModal = React.forwardRef<
  ModelConfirmDeleteModalInstance,
  { onDeleted: () => void }
>(({ onDeleted }, ref) => {
  const deleteIdRef = useRef<string>();
  const [visible, setVisible] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const { start: startPolling } = usePollingUntil({
    continueChecker: async () => {
      if (!deleteIdRef.current) {
        throw new NoIdProvideError();
      }
      return (
        (
          await APIProvider.getAPI('model').search({
            ids: [deleteIdRef.current],
            pageSize: 1,
            currentPage: 1,
          })
        ).pagination.totalRecords === 1
      );
    },
    onGiveUp: () => {
      setIsDeleting(false);
      setVisible(false);
      onDeleted();
    },
    onMaxRetries: () => {
      setIsDeleting(false);
      setVisible(false);
    },
  });

  const handleConfirm = useCallback(
    async (e) => {
      if (!deleteIdRef.current) {
        throw new NoIdProvideError();
      }
      e.stopPropagation();
      setIsDeleting(true);
      await APIProvider.getAPI('model').delete(deleteIdRef.current);
      startPolling();
    },
    [startPolling]
  );

  const handleCancel = useCallback(() => {
    setVisible(false);
    deleteIdRef.current = undefined;
  }, []);

  useImperativeHandle(
    ref,
    () => ({
      show: (id: string) => {
        deleteIdRef.current = id;
        setVisible(true);
      },
    }),
    []
  );

  if (!visible) {
    return null;
  }

  return (
    <EuiConfirmModal
      title="Confirm Delete?"
      cancelButtonText="Cancel"
      confirmButtonText="Confirm"
      onCancel={handleCancel}
      onConfirm={handleConfirm}
      isLoading={isDeleting}
    />
  );
});
