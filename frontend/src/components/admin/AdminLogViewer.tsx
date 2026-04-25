import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../utils/api.ts';
import { Search, Loader2, ChevronDown } from 'lucide-react';

interface AdminLog {
    id: string;
    adminUsername: string;
    action: string;
    targetId: string;
    targetType: string;
    details: string;
    timestamp: string;
}

const ACTION_OPTIONS = [
    { value: "BAN_EMAIL", label: "BAN_EMAIL" },
    { value: "UNBAN_EMAIL", label: "UNBAN_EMAIL" },
    { value: "DELETE_USER", label: "DELETE_USER" },
    { value: "UPDATE_TIER", label: "UPDATE_TIER" },
    { value: "ADD_ROLE", label: "ADD_ROLE" },
    { value: "REMOVE_ROLE", label: "REMOVE_ROLE" },
    { value: "PUBLISH_PROJECT", label: "PUBLISH_PROJECT" },
    { value: "APPROVE_VERSION", label: "APPROVE_VERSION" },
    { value: "REJECT_VERSION", label: "REJECT_VERSION" },
    { value: "REJECT_PROJECT", label: "REJECT_PROJECT" },
    { value: "DELETE_PROJECT", label: "DELETE_PROJECT" },
    { value: "HARD_DELETE_PROJECT", label: "HARD_DELETE_PROJECT" },
    { value: "RESTORE_PROJECT", label: "RESTORE_PROJECT" },
    { value: "UNLIST_PROJECT", label: "UNLIST_PROJECT" },
    { value: "DELETE_VERSION", label: "DELETE_VERSION" },
    { value: "RAW_UPDATE_PROJECT", label: "RAW_UPDATE_PROJECT" },
    { value: "RAW_UPDATE_USER", label: "RAW_UPDATE_USER" },
    { value: "RESCAN_VERSION", label: "RESCAN_VERSION" },
];

const TARGET_OPTIONS = [
    { value: "USER", label: "USER" },
    { value: "PROJECT", label: "PROJECT" },
    { value: "VERSION", label: "VERSION" },
    { value: "EMAIL", label: "EMAIL" },
];

const CustomSelect = ({
                          value,
                          onChange,
                          options,
                          placeholder
                      }: {
    value: string,
    onChange: (val: string) => void,
    options: {label: string, value: string}[],
    placeholder: string
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (ref.current && !ref.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const selectedLabel = options.find(opt => opt.value === value)?.label || placeholder;

    return (
        <div className="relative w-full sm:w-48" ref={ref}>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full px-4 py-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-modtale-accent flex justify-between items-center text-slate-700 dark:text-slate-300"
            >
                <span className="truncate">{selectedLabel}</span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className="absolute z-10 w-full mt-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg shadow-xl max-h-60 overflow-auto py-1">
                    <button
                        type="button"
                        onClick={() => { onChange(''); setIsOpen(false); }}
                        className={`w-full text-left px-4 py-2 text-sm hover:bg-slate-100 dark:hover:bg-white/5 transition-colors ${value === '' ? 'text-modtale-accent font-medium' : 'text-slate-700 dark:text-slate-300'}`}
                    >
                        {placeholder}
                    </button>
                    {options.map((opt) => (
                        <button
                            key={opt.value}
                            type="button"
                            onClick={() => { onChange(opt.value); setIsOpen(false); }}
                            className={`w-full text-left px-4 py-2 text-sm hover:bg-slate-100 dark:hover:bg-white/5 transition-colors ${value === opt.value ? 'text-modtale-accent font-medium' : 'text-slate-700 dark:text-slate-300'}`}
                        >
                            {opt.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export const AdminLogViewer: React.FC = () => {
    const [logs, setLogs] = useState<AdminLog[]>([]);
    const [loading, setLoading] = useState(false);
    const [query, setQuery] = useState('');
    const [actionFilter, setActionFilter] = useState('');
    const [targetTypeFilter, setTargetTypeFilter] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);

    const fetchLogs = async () => {
        setLoading(true);
        try {
            const res = await api.get('/admin/logs', {
                params: { query, action: actionFilter, targetType: targetTypeFilter, page, size: 50 }
            });
            setLogs(res.data.content || []);
            setTotalPages(res.data.totalPages || 1);
        } catch (e) {
            console.error('Failed to fetch logs', e);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLogs();
    }, [page, actionFilter, targetTypeFilter]);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        fetchLogs();
    };

    return (
        <div className="space-y-4">
            <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-4 mb-6 bg-white dark:bg-modtale-card p-4 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        placeholder="Search IDs, Users, or Details..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        className="w-full pl-9 pr-4 py-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-modtale-accent"
                    />
                </div>
                <CustomSelect
                    value={actionFilter}
                    onChange={(val) => { setActionFilter(val); setPage(0); }}
                    options={ACTION_OPTIONS}
                    placeholder="All Actions"
                />
                <CustomSelect
                    value={targetTypeFilter}
                    onChange={(val) => { setTargetTypeFilter(val); setPage(0); }}
                    options={TARGET_OPTIONS}
                    placeholder="All Targets"
                />
                <button type="submit" className="px-6 py-2 bg-modtale-accent text-white rounded-lg text-sm font-bold hover:bg-modtale-accent/90 transition-colors">
                    Search
                </button>
            </form>

            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-xl shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-slate-600 dark:text-slate-400">
                        <thead className="bg-slate-50 dark:bg-white/5 text-slate-900 dark:text-white font-semibold">
                        <tr>
                            <th className="px-4 py-3 border-b border-slate-200 dark:border-white/5">Time</th>
                            <th className="px-4 py-3 border-b border-slate-200 dark:border-white/5">Admin</th>
                            <th className="px-4 py-3 border-b border-slate-200 dark:border-white/5">Action</th>
                            <th className="px-4 py-3 border-b border-slate-200 dark:border-white/5">Target</th>
                            <th className="px-4 py-3 border-b border-slate-200 dark:border-white/5">Details</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-white/5">
                        {loading ? (
                            <tr>
                                <td colSpan={5} className="px-4 py-8 text-center">
                                    <Loader2 className="w-6 h-6 animate-spin text-modtale-accent mx-auto" />
                                </td>
                            </tr>
                        ) : logs.length === 0 ? (
                            <tr>
                                <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                                    No logs found matching your filters.
                                </td>
                            </tr>
                        ) : (
                            logs.map((log) => (
                                <tr key={log.id} className="hover:bg-slate-50 dark:hover:bg-white/5 transition-colors">
                                    <td className="px-4 py-3 whitespace-nowrap">{new Date(log.timestamp).toLocaleString()}</td>
                                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">{log.adminUsername}</td>
                                    <td className="px-4 py-3">
                                            <span className="px-2 py-1 text-xs rounded-md bg-slate-100 dark:bg-white/10 font-mono">
                                                {log.action}
                                            </span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span className="text-xs text-slate-500 block mb-0.5">{log.targetType}</span>
                                        <span className="font-mono text-xs">{log.targetId}</span>
                                    </td>
                                    <td className="px-4 py-3 max-w-sm truncate" title={log.details || ''}>
                                        {log.details || '-'}
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            </div>

            {totalPages > 1 && (
                <div className="flex justify-between items-center px-4 py-3 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-xl">
                    <button
                        disabled={page === 0}
                        onClick={() => setPage(p => Math.max(0, p - 1))}
                        className="px-4 py-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-100 dark:hover:bg-white/5 text-slate-700 dark:text-slate-300 rounded-lg transition-colors"
                    >
                        Previous
                    </button>
                    <span className="text-sm text-slate-500">
                        Page {page + 1} of {totalPages}
                    </span>
                    <button
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                        className="px-4 py-2 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-100 dark:hover:bg-white/5 text-slate-700 dark:text-slate-300 rounded-lg transition-colors"
                    >
                        Next
                    </button>
                </div>
            )}
        </div>
    );
};