import { useEffect } from 'react';

let scrollLockCount = 0;
let originalOverflow = '';

export const useScrollLock = (lock: boolean) => {
    useEffect(() => {
        if (lock) {
            if (scrollLockCount === 0) originalOverflow = document.body.style.overflow;
            scrollLockCount++;
            document.body.style.overflow = 'hidden';
        }
        return () => {
            if (lock) {
                scrollLockCount--;
                if (scrollLockCount <= 0) {
                    scrollLockCount = 0;
                    document.body.style.overflow = originalOverflow;
                }
            }
        };
    }, [lock]);
};