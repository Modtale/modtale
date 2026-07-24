import React, { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import {
    ChevronDown,
    History,
    Loader2,
    Search,
} from 'lucide-react';
import { adminClient } from '../api/adminClient';
import { EmptyState } from '@/components/ui/EmptyState';
import { extractApiErrorMessage } from '@/utils/api';

interface AdminLog {
    id: string;
    adminUsername: string;
    action: string;
    targetId: string;
    targetType: string;
    details: string;
    timestamp: string;
}

interface LogOption {
    value: string;
    label: string;
}

const ACTION_OPTIONS: LogOption[] = [
    { value: 'BAN_EMAIL', label: 'Ban Email' },
    { value: 'UNBAN_EMAIL', label: 'Unban Email' },
    { value: 'DELETE_USER', label: 'Delete User' },
    { value: 'UPDATE_TIER', label: 'Update Tier' },
    { value: 'UPDATE_ADMIN_PERMISSIONS', label: 'Update Admin Permissions' },
    { value: 'PUBLISH_PROJECT', label: 'Publish Project' },
    { value: 'APPROVE_VERSION', label: 'Approve Version' },
    { value: 'REJECT_VERSION', label: 'Reject Version' },
    { value: 'REJECT_PROJECT', label: 'Reject Project' },
    { value: 'DELETE_PROJECT', label: 'Delete Project' },
    { value: 'HARD_DELETE_PROJECT', label: 'Hard Delete Project' },
    { value: 'RESTORE_PROJECT', label: 'Restore Project' },
    { value: 'UNLIST_PROJECT', label: 'Unlist Project' },
    { value: 'DELETE_VERSION', label: 'Delete Version' },
    { value: 'RAW_UPDATE_PROJECT', label: 'Raw Update Project' },
    { value: 'RAW_UPDATE_USER', label: 'Raw Update User' },
    { value: 'RESCAN_VERSION', label: 'Rescan Version' },
];

const TARGET_OPTIONS: LogOption[] = [
    { value: 'USER', label: 'User' },
    { value: 'PROJECT', label: 'Project' },
    { value: 'VERSION', label: 'Version' },
    { value: 'EMAIL', label: 'Email' },
];

function formatActionLabel(action: string) {
    return action
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ');
}

function getActionTone(action: string) {
    if (action.includes('DELETE') || action.includes('REJECT') || action.includes('BAN')) {
        return 'bg-red-50 text-red-700 border-red-200 dark:bg-red-500/10 dark:text-red-300 dark:border-red-500/20';
    }

    if (action.includes('APPROVE') || action.includes('PUBLISH') || action.includes('RESTORE') || action.includes('ADD_')) {
        return 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/20';
    }

    if (action.includes('UPDATE') || action.includes('RESCAN') || action.includes('REMOVE_') || action.includes('UNLIST')) {
        return 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/20';
    }

    return 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-white/10 dark:text-slate-200 dark:border-white/10';
}

function getTargetTone(targetType: string) {
    switch (targetType) {
        case 'USER':
            return 'text-sky-600 dark:text-sky-300';
        case 'PROJECT':
            return 'text-violet-600 dark:text-violet-300';
        case 'VERSION':
            return 'text-orange-600 dark:text-orange-300';
        case 'EMAIL':
            return 'text-emerald-600 dark:text-emerald-300';
        default:
            return 'text-slate-500 dark:text-slate-400';
    }
}

function FilterSelect({
    value,
    onChange,
    options,
    placeholder,
    isOpen,
    onToggle,
}: {
    value: string;
    onChange: (next: string) => void;
    options: LogOption[];
    placeholder: string;
    isOpen: boolean;
    onToggle: () => void;
}) {
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (ref.current && !ref.current.contains(event.target as Node) && isOpen) {
                onToggle();
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen, onToggle]);

    const selectedLabel = options.find((option) => option.value === value)?.label || placeholder;

    return (
        <div className="relative w-full xl:w-44" ref={ref}>
            <button
                type="button"
                onClick={onToggle}
                className="flex h-12 w-full items-center justify-between rounded-2xl border border-slate-200 bg-white/80 px-4 text-left text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:border-slate-300 hover:bg-white focus:outline-none focus:ring-2 focus:ring-modtale-accent/20 dark:border-white/10 dark:bg-black/20 dark:text-slate-200 dark:hover:border-white/15 dark:hover:bg-black/30"
            >
                <span className="truncate">{selectedLabel}</span>
                <ChevronDown className={`h-4 w-4 shrink-0 text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>

            {isOpen && (
                <div className="absolute left-0 top-full z-30 mt-2 w-full overflow-hidden rounded-2xl border border-slate-200 bg-white/95 p-1 shadow-2xl backdrop-blur-xl dark:border-white/10 dark:bg-slate-900/95">
                    <button
                        type="button"
                        onClick={() => {
                            onChange('');
                            onToggle();
                        }}
                        className={`w-full rounded-xl px-3 py-2 text-left text-sm transition-colors hover:bg-slate-100 dark:hover:bg-white/5 ${
                            value === '' ? 'bg-slate-100 font-semibold text-slate-900 dark:bg-white/10 dark:text-white' : 'text-slate-600 dark:text-slate-300'
                        }`}
                    >
                        {placeholder}
                    </button>
                    {options.map((option) => (
                        <button
                            key={option.value}
                            type="button"
                            onClick={() => {
                                onChange(option.value);
                                onToggle();
                            }}
                            className={`w-full rounded-xl px-3 py-2 text-left text-sm transition-colors hover:bg-slate-100 dark:hover:bg-white/5 ${
                                value === option.value ? 'bg-slate-100 font-semibold text-slate-900 dark:bg-white/10 dark:text-white' : 'text-slate-600 dark:text-slate-300'
                            }`}
                        >
                            {option.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

function AuditLogsSkeleton() {
    return (
        <div className="space-y-6 animate-pulse">
            <div className="rounded-3xl border border-slate-200 bg-slate-200/60 p-6 dark:border-white/10 dark:bg-white/5">
                <div className="mb-4 h-6 w-40 rounded-xl bg-slate-300/70 dark:bg-white/10" />
                <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.6fr),minmax(0,0.6fr),minmax(0,0.6fr),auto,auto]">
                    {[...Array(5)].map((_, index) => (
                        <div key={index} className="h-12 rounded-2xl bg-slate-300/70 dark:bg-white/10" />
                    ))}
                </div>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-slate-200/60 dark:border-white/10 dark:bg-white/5">
                {[...Array(5)].map((_, index) => (
                    <div key={index} className="h-20 border-b border-slate-300/70 dark:border-white/10 last:border-b-0" />
                ))}
            </div>
        </div>
    );
}

export function AuditLogs() {
    const [logs, setLogs] = useState<AdminLog[]>([]);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState('');
    const [appliedQuery, setAppliedQuery] = useState('');
    const [actionFilter, setActionFilter] = useState('');
    const [targetTypeFilter, setTargetTypeFilter] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [openMenu, setOpenMenu] = useState<'action' | 'target' | null>(null);

    const fetchLogs = async () => {
        setLoading(true);
        try {
            const data = await adminClient.getLogs({
                query: appliedQuery,
                action: actionFilter,
                targetType: targetTypeFilter,
                page,
                size: 50,
            });

            setLogs(data.content || []);
            setTotalPages(data.totalPages || 1);
            setTotalElements(data.totalElements || 0);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(extractApiErrorMessage(error, 'We could not load the audit logs.'));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLogs();
    }, [page, actionFilter, targetTypeFilter, appliedQuery]);

    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setPage(0);
        setAppliedQuery(query.trim());
    };

    const clearFilters = () => {
        setQuery('');
        setAppliedQuery('');
        setActionFilter('');
        setTargetTypeFilter('');
        setOpenMenu(null);
        setPage(0);
    };

    if (loading && logs.length === 0) {
        return <AuditLogsSkeleton />;
    }

    return (
        <div className="space-y-6">
            {errorMessage && (
                <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300">
                    {errorMessage}
                </div>
            )}

            <form
                onSubmit={handleSearch}
                className="relative z-20 rounded-3xl border border-slate-200 bg-white/50 p-6 shadow-sm backdrop-blur-md dark:border-white/10 dark:bg-white/5"
            >
                <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white">Filter activity</h3>
                        <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Search actor names, IDs, or details to narrow the activity stream.</p>
                    </div>
                </div>

                <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1.6fr),11rem,11rem,auto,auto]">
                    <div className="relative">
                        <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            type="text"
                            placeholder="Search admins, targets, IDs, or log details..."
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            className="h-12 w-full rounded-2xl border border-slate-200 bg-white/80 pl-11 pr-4 text-sm font-medium text-slate-900 shadow-sm outline-none transition-all placeholder:text-slate-400 focus:border-modtale-accent focus:ring-2 focus:ring-modtale-accent/20 dark:border-white/10 dark:bg-black/20 dark:text-white"
                        />
                    </div>

                    <FilterSelect
                        value={actionFilter}
                        onChange={(next) => {
                            setActionFilter(next);
                            setPage(0);
                        }}
                        options={ACTION_OPTIONS}
                        placeholder="All Actions"
                        isOpen={openMenu === 'action'}
                        onToggle={() => setOpenMenu((current) => current === 'action' ? null : 'action')}
                    />

                    <FilterSelect
                        value={targetTypeFilter}
                        onChange={(next) => {
                            setTargetTypeFilter(next);
                            setPage(0);
                        }}
                        options={TARGET_OPTIONS}
                        placeholder="All Targets"
                        isOpen={openMenu === 'target'}
                        onToggle={() => setOpenMenu((current) => current === 'target' ? null : 'target')}
                    />

                    <button
                        type="submit"
                        className="h-12 rounded-2xl bg-modtale-accent px-5 text-sm font-bold text-white shadow-lg shadow-modtale-accent/20 transition-colors hover:bg-modtale-accent/90"
                    >
                        Search
                    </button>

                    <button
                        type="button"
                        onClick={clearFilters}
                        className="h-12 rounded-2xl border border-slate-200 bg-white/80 px-5 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50 dark:border-white/10 dark:bg-black/20 dark:text-slate-200 dark:hover:bg-white/5"
                    >
                        Clear
                    </button>
                </div>
            </form>

            <div className="relative z-0 overflow-hidden rounded-3xl border border-slate-200 bg-white/45 shadow-sm backdrop-blur-md dark:border-white/10 dark:bg-white/5">
                <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-white/10">
                    <div>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white">Audit events</h3>
                        <p className="text-sm font-medium text-slate-500 dark:text-slate-400">Recent administrative actions with actor, target, and detail context.</p>
                    </div>
                    {loading && (
                        <div className="hidden items-center gap-2 text-xs font-black uppercase tracking-[0.2em] text-slate-400 sm:flex">
                            <Loader2 className="h-4 w-4 animate-spin text-modtale-accent" />
                            Refreshing
                        </div>
                    )}
                </div>

                {logs.length === 0 ? (
                    <div className="p-6">
                        <EmptyState
                            icon={History}
                            title="No audit logs match these filters"
                            message="Try broadening the search or clearing one of the filters to bring more activity back into view."
                            onAction={clearFilters}
                            actionLabel="Clear filters"
                        />
                    </div>
                ) : (
                    <>
                        <div className="hidden overflow-x-auto lg:block">
                            <table className="min-w-full text-left">
                                <thead className="bg-white/70 text-[11px] font-black uppercase tracking-[0.2em] text-slate-500 dark:bg-black/20 dark:text-slate-400">
                                    <tr>
                                        <th className="px-6 py-4">Event</th>
                                        <th className="px-6 py-4">Admin</th>
                                        <th className="px-6 py-4">Target</th>
                                        <th className="px-6 py-4">Details</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200 dark:divide-white/10">
                                    {logs.map((log) => (
                                        <tr key={log.id} className="align-top transition-colors hover:bg-white/80 dark:hover:bg-white/[0.03]">
                                            <td className="px-6 py-5">
                                                <div className="flex flex-col gap-3">
                                                    <div className={`inline-flex w-fit items-center rounded-full border px-3 py-1 text-xs font-bold ${getActionTone(log.action)}`}>
                                                        {formatActionLabel(log.action)}
                                                    </div>
                                                    <div>
                                                        <div className="text-sm font-semibold text-slate-900 dark:text-white">{new Date(log.timestamp).toLocaleString()}</div>
                                                        <div className="mt-1 font-mono text-xs text-slate-400">{log.id}</div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-5">
                                                <div className="text-sm font-bold text-slate-900 dark:text-white">{log.adminUsername}</div>
                                                <div className="mt-1 text-xs font-medium uppercase tracking-[0.16em] text-slate-400">Actor</div>
                                            </td>
                                            <td className="px-6 py-5">
                                                <div className={`text-xs font-black uppercase tracking-[0.18em] ${getTargetTone(log.targetType)}`}>{log.targetType}</div>
                                                <div className="mt-2 break-all font-mono text-sm text-slate-700 dark:text-slate-200">{log.targetId || 'Unknown target'}</div>
                                            </td>
                                            <td className="px-6 py-5">
                                                <p className="max-w-xl whitespace-pre-wrap break-words text-sm leading-6 text-slate-600 dark:text-slate-300">
                                                    {log.details?.trim() || 'No additional details recorded.'}
                                                </p>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        <div className="space-y-4 p-4 lg:hidden">
                            {logs.map((log) => (
                                <article
                                    key={log.id}
                                    className="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm dark:border-white/10 dark:bg-black/20"
                                >
                                    <div className="flex flex-wrap items-start justify-between gap-3">
                                        <div className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-bold ${getActionTone(log.action)}`}>
                                            {formatActionLabel(log.action)}
                                        </div>
                                        <div className="text-right text-xs font-medium text-slate-400">
                                            <div>{new Date(log.timestamp).toLocaleString()}</div>
                                            <div className="mt-1 font-mono">{log.id}</div>
                                        </div>
                                    </div>

                                    <div className="mt-4 grid gap-4 sm:grid-cols-2">
                                        <div>
                                            <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">Admin</div>
                                            <div className="mt-1 text-sm font-bold text-slate-900 dark:text-white">{log.adminUsername}</div>
                                        </div>
                                        <div>
                                            <div className={`text-[11px] font-black uppercase tracking-[0.18em] ${getTargetTone(log.targetType)}`}>{log.targetType}</div>
                                            <div className="mt-1 break-all font-mono text-sm text-slate-700 dark:text-slate-200">{log.targetId || 'Unknown target'}</div>
                                        </div>
                                    </div>

                                    <div className="mt-4 border-t border-slate-200 pt-4 dark:border-white/10">
                                        <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">Details</div>
                                        <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-slate-600 dark:text-slate-300">
                                            {log.details?.trim() || 'No additional details recorded.'}
                                        </p>
                                    </div>
                                </article>
                            ))}
                        </div>
                    </>
                )}
            </div>

            {totalPages > 1 && (
                <div className="flex flex-col gap-4 rounded-3xl border border-slate-200 bg-white/50 px-5 py-4 shadow-sm backdrop-blur-md sm:flex-row sm:items-center sm:justify-between dark:border-white/10 dark:bg-white/5">
                    <div>
                        <div className="text-sm font-bold text-slate-900 dark:text-white">
                            Page {page + 1} of {totalPages}
                        </div>
                        <div className="text-sm font-medium text-slate-500 dark:text-slate-400">
                            Browsing {totalElements.toLocaleString()} matching audit events.
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            disabled={page === 0}
                            onClick={() => setPage((current) => Math.max(0, current - 1))}
                            className="rounded-2xl border border-slate-200 bg-white/80 px-4 py-2.5 text-sm font-bold text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-white/10 dark:bg-black/20 dark:text-slate-200 dark:hover:bg-white/5"
                        >
                            Previous
                        </button>
                        <button
                            type="button"
                            disabled={page >= totalPages - 1}
                            onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                            className="rounded-2xl bg-modtale-accent px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-modtale-accent/90 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            Next
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
