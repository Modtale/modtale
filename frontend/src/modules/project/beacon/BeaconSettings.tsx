import { ArrowUpRight } from 'lucide-react';
import { theme } from '@/styles/theme';

export function BeaconSettings() {
    return (
        <div className={`mt-4 border-t ${theme.colors.borderFaint} pt-4`}>
            <h3 className={`text-sm font-bold ${theme.colors.textPrimary}`}>ModStats</h3>
            <p className={`mt-1 text-xs leading-relaxed ${theme.colors.textMuted}`}>
                Register on ModStats, add your Modtale project URL, and make your stats public.
                Your active server and player counts will automatically appear on your Modtale project page.
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2">
                <a href="https://beacon.modstats.io/portal" target="_blank" rel="noreferrer" className={`inline-flex items-center gap-1 text-xs font-bold ${theme.colors.textSecondary} hover:text-modtale-accent transition-colors`}>
                    Get started on ModStats <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
                </a>
                <a href="https://wiki.hytalemodding.dev/mod/beacon/quick-stats-setup" target="_blank" rel="noreferrer" className={`inline-flex items-center gap-1 text-xs ${theme.colors.textMuted} hover:text-modtale-accent transition-colors`}>
                    Setup guide <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
                </a>
            </div>
        </div>
    );
}
