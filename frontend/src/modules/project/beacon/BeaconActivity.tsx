import { Activity, ArrowUpRight, Server, Users } from 'lucide-react';
import { SidebarSection } from '../components/ProjectLayout';
import { useBeaconStats } from './useBeaconStats';

export function BeaconActivity({ projectId }: { projectId: string }) {
    const { stats } = useBeaconStats(projectId, '24h');
    if (!stats) return null;

    const lastHeartbeat = stats.lastHeartbeatAt
        ? `Last heartbeat: ${new Date(stats.lastHeartbeatAt).toLocaleString()}`
        : 'Waiting for the first heartbeat';

    return (
        <SidebarSection title="Activity" icon={Activity}>
            <dl className="grid grid-cols-2 gap-2 pt-5 pb-2" title={lastHeartbeat}>
                <div className="flex flex-col items-center">
                    <dt className="order-2 mt-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400">
                        <Server className="h-3.5 w-3.5" aria-hidden="true" />
                        Active servers
                    </dt>
                    <dd className="order-1 text-2xl font-black tabular-nums tracking-tighter leading-none text-slate-900 dark:text-white">{stats.activeServers.toLocaleString()}</dd>
                </div>
                <div className="flex flex-col items-center border-l border-slate-200 dark:border-white/5">
                    <dt className="order-2 mt-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400">
                        <Users className="h-3.5 w-3.5" aria-hidden="true" />
                        Active players
                    </dt>
                    <dd className="order-1 text-2xl font-black tabular-nums tracking-tighter leading-none text-slate-900 dark:text-white">{stats.activePlayers.toLocaleString()}</dd>
                </div>
            </dl>
            <a
                href={`https://modstats.io/stats/${encodeURIComponent(stats.slug)}`}
                target="_blank"
                rel="noreferrer"
                title="Public, anonymous usage stats from reporting servers. View history and more on ModStats."
                className="mt-1 ml-auto flex w-fit items-center gap-1 text-[10px] font-medium text-slate-500 dark:text-slate-400 hover:text-modtale-accent transition-colors"
            >
                ModStats <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
            </a>
        </SidebarSection>
    );
}
