import { theme } from '@/styles/theme';

// Use the same shell as DownloadModal and DependencyModal.
export const modpackDialog = {
    content: `${theme.components.modalContent} w-full max-w-lg max-h-[85dvh]`,
    header: `${theme.components.modalHeader} gap-4`,
    title: `text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2 min-w-0`,
    close: `p-2 rounded-full shrink-0 ${theme.colors.bgSurfaceHover} ${theme.colors.textMuted} transition-colors`,
    body: `${theme.components.modalBody} min-h-0`,
    footer: `${theme.components.modalFooter} gap-3`,
    row: `p-4 rounded-2xl border ${theme.colors.border} ${theme.colors.bgBase}`,
};
