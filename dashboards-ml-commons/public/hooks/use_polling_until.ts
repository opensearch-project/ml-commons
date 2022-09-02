import { useCallback, useEffect, useRef } from 'react';

export const usePollingUntil = ({
  pollingGap = 300,
  maxRetries = 100,
  continueChecker,
  onMaxRetries,
  onGiveUp,
}: {
  pollingGap?: number;
  maxRetries?: number;
  continueChecker: () => Promise<Boolean>;
  onGiveUp: () => void;
  onMaxRetries: () => void;
}) => {
  const pollingIntervalRef = useRef(-1);
  const continueCheckerRef = useRef(continueChecker);
  continueCheckerRef.current = continueChecker;
  const pollingTimes = useRef(0);
  const onMaxRetiresRef = useRef(onMaxRetries);
  onMaxRetiresRef.current = onMaxRetries;
  const onGiveUpRef = useRef(onGiveUp);
  onGiveUpRef.current = onGiveUp;

  const start = useCallback(() => {
    const stop = () => {
      pollingTimes.current = 0;
      window.clearInterval(pollingIntervalRef.current);
    };
    pollingIntervalRef.current = window.setInterval(() => {
      if (pollingTimes.current > maxRetries) {
        stop();
        onMaxRetiresRef.current();
        return;
      }
      continueCheckerRef.current().then((flag) => {
        if (!flag) {
          stop();
          onGiveUpRef.current();
        }
      });
    }, pollingGap);
  }, [pollingGap, maxRetries, continueChecker]);

  useEffect(() => {
    return () => {
      window.clearInterval(pollingIntervalRef.current);
    };
  });

  return {
    start,
  };
};
