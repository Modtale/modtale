import { theme } from '@/styles/theme';

export const modpackDialog = {
    content: `${theme.components.modalContent} w-full max-w-lg max-h-[85dvh]`,
    header: 'px-6 pt-5 pb-4 flex items-center justify-between gap-4 shrink-0',
    body: 'px-6 pb-5 space-y-4 overflow-y-auto min-h-0 flex-1',
    footer: 'px-6 pb-5 pt-2 flex items-center justify-end gap-3 shrink-0',
};
