import { useEffect } from 'react';

let scrollLockCount = 0;

export const useScrollLock = (lock: boolean) => {
    useEffect(() => {
        if (lock) {
            scrollLockCount++;
            document.body.style.overflow = 'hidden';
        }
        return () => {
            if (lock) {
                scrollLockCount--;
                if (scrollLockCount <= 0) {
                    scrollLockCount = 0;
                    document.body.style.overflow = '';
                }
            }
        };
    }, [lock]);
};