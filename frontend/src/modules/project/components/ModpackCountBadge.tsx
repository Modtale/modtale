import { Box } from 'lucide-react';

export const ModpackCountBadge = ({ count }: { count?: number }) => count === undefined ? null : (
    <span aria-label={`${count} mods`} className="absolute bottom-0 right-0 z-20 bg-slate-900/85 backdrop-blur-sm text-white text-[max(8px,14cqw)] font-bold px-[0.35em] py-[0.15em] rounded-tl-[0.7em] flex items-center pointer-events-none">
        <Box className="w-[1.15em] h-[1.15em] mr-[0.15em]" aria-hidden="true" />{count}
    </span>
);
