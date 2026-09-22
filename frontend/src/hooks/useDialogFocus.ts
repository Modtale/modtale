import { useEffect, type RefObject } from 'react';

/** Keep keyboard focus in an open dialog and restore the invoking control. */
export function useDialogFocus(open: boolean, ref: RefObject<HTMLElement | null>) {
    useEffect(() => {
        if (!open) return;
        const previous = document.activeElement as HTMLElement | null;
        const controls = () => Array.from(ref.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled):not([type="file"]), select:not(:disabled), textarea, summary, a[href]') || [])
            .filter(node => node.offsetParent !== null);
        if (!ref.current?.contains(document.activeElement)) controls()[0]?.focus();
        const keydown = (event: KeyboardEvent) => {
            if (event.key !== 'Tab') return;
            const items = controls(), first = items[0], last = items.at(-1);
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
        };
        document.addEventListener('keydown', keydown);
        return () => { document.removeEventListener('keydown', keydown); if (previous?.isConnected) previous.focus(); };
    }, [open, ref]);
}
