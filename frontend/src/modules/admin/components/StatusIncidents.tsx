import React, { useEffect, useMemo, useState } from 'react';
import { Activity, CalendarClock, CheckCircle2, Clock3, Megaphone, RefreshCw, Send } from 'lucide-react';
import { adminClient } from '../api/adminClient';
import { extractApiErrorMessage } from '@/utils/api';
import { CalendarWidget } from '@/components/ui/CalendarWidget';

type IncidentKind = 'INCIDENT' | 'MAINTENANCE';
type IncidentState = 'SCHEDULED' | 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED' | 'CANCELED';
type SystemStatus = 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE';

interface StatusIncidentUpdate {
    id: string;
    state: IncidentState;
    impact: SystemStatus;
    message: string;
    createdAt: string;
    createdByUsername?: string;
}

interface StatusIncident {
    id: string;
    kind: IncidentKind;
    state: IncidentState;
    impact: SystemStatus;
    title: string;
    affectedServices: string[];
    scheduledStart?: string;
    scheduledEnd?: string;
    startedAt?: string;
    resolvedAt?: string;
    createdAt: string;
    updatedAt: string;
    createdByUsername?: string;
    updates: StatusIncidentUpdate[];
}

interface StatusIncidentsProps {
    setStatus: (status: any) => void;
    canManage?: boolean;
}

const serviceOptions = ['api', 'database', 'storage'];
const states: IncidentState[] = ['SCHEDULED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED', 'CANCELED'];
const impacts: SystemStatus[] = ['OPERATIONAL', 'DEGRADED', 'OUTAGE'];

const formatDate = (value?: string) => {
    if (!value) return 'Not set';
    return new Date(value).toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

const stateLabel = (state: IncidentState) => state.replace('_', ' ').toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
const impactLabel = (impact: SystemStatus) => impact.toLowerCase().replace(/^\w/, (char) => char.toUpperCase());

const stateClass = (state: IncidentState) => {
    if (state === 'RESOLVED') return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-400';
    if (state === 'CANCELED') return 'border-slate-200 bg-slate-100 text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300';
    if (state === 'SCHEDULED') return 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-500/20 dark:bg-blue-500/10 dark:text-blue-400';
    return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-400';
};

const pad = (value: number) => String(value).padStart(2, '0');

const datePartFromDate = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

const getDatePart = (value: string) => value.split('T')[0] || '';
const getTimePart = (value: string) => value.split('T')[1]?.slice(0, 5) || '09:00';

const parseDatePart = (value: string) => {
    const datePart = getDatePart(value);
    if (!datePart) return null;
    const [year, month, day] = datePart.split('-').map(Number);
    if (!year || !month || !day) return null;
    return new Date(year, month - 1, day);
};

const withDatePart = (value: string, date: Date) => `${datePartFromDate(date)}T${getTimePart(value)}`;

const withTimePart = (value: string, time: string) => {
    const datePart = getDatePart(value) || datePartFromDate(new Date());
    return `${datePart}T${time}`;
};

const normalizeDateTimeValue = (value: string) => {
    const datePart = getDatePart(value);
    if (!datePart) return null;
    const [rawHour = '9', rawMinute = '0'] = getTimePart(value).split(':');
    const hour = Math.min(23, Math.max(0, Number(rawHour) || 0));
    const minute = Math.min(59, Math.max(0, Number(rawMinute) || 0));
    return `${datePart}T${pad(hour)}:${pad(minute)}`;
};

const formatDateTimeInput = (value: string) => {
    const date = parseDatePart(value);
    if (!date) return null;
    const [hour, minute] = getTimePart(value).split(':');
    date.setHours(Number(hour) || 0, Number(minute) || 0, 0, 0);
    return date.toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

const StatusDateTimePicker = ({
    label,
    value,
    onChange,
    placeholder = 'Pick date and time',
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const selectedDate = parseDatePart(value);
    const timeValue = value ? getTimePart(value) : '';
    const displayValue = formatDateTimeInput(value);

    const handleTimeChange = (nextTime: string) => {
        if (!/^\d{0,2}:?\d{0,2}$/.test(nextTime)) return;
        onChange(withTimePart(value, nextTime));
    };

    const commitTimeBlur = () => {
        if (!value && !timeValue) return;
        const [rawHour = '9', rawMinute = '0'] = (timeValue || '09:00').split(':');
        const hour = Math.min(23, Math.max(0, Number(rawHour) || 0));
        const minute = Math.min(59, Math.max(0, Number(rawMinute) || 0));
        onChange(withTimePart(value, `${pad(hour)}:${pad(minute)}`));
    };

    return (
        <div className="space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            {label}
            <div className="relative">
                <button
                    type="button"
                    onClick={() => setIsOpen((current) => !current)}
                    className={`flex w-full items-center justify-between rounded-xl border px-3 py-2.5 text-left text-sm font-semibold shadow-sm transition hover:bg-slate-100 dark:hover:bg-white/10 ${
                        value
                            ? 'border-modtale-accent bg-modtale-accent/5 text-slate-900 dark:text-white'
                            : 'border-slate-200 bg-slate-50 text-slate-500 dark:border-white/10 dark:bg-black/20 dark:text-slate-300'
                    }`}
                >
                    <span className="flex min-w-0 items-center gap-2">
                        <CalendarClock className="h-4 w-4 shrink-0 text-modtale-accent" />
                        <span className="truncate">{displayValue || placeholder}</span>
                    </span>
                    <span className="text-[10px] font-black uppercase text-slate-400">{isOpen ? 'Close' : 'Edit'}</span>
                </button>

                {isOpen && (
                    <div className="absolute left-0 top-full z-50 mt-2 w-full min-w-[17rem] rounded-2xl border border-slate-200 bg-white p-3 shadow-2xl dark:border-white/10 dark:bg-slate-900">
                        <CalendarWidget
                            selectedDate={selectedDate}
                            onSelect={(date) => onChange(withDatePart(value, date))}
                            allowFutureDates
                            className="border-0 bg-transparent p-0 shadow-none"
                        />
                        <div className="mt-3 flex items-end gap-2 border-t border-slate-200 pt-3 dark:border-white/10">
                            <label className="min-w-0 flex-1 space-y-1 text-[10px] font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">
                                Time
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    placeholder="09:00"
                                    value={timeValue}
                                    onChange={(event) => handleTimeChange(event.target.value)}
                                    onBlur={commitTimeBlur}
                                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-bold text-slate-900 outline-none focus:border-modtale-accent dark:border-white/10 dark:bg-black/20 dark:text-white"
                                />
                            </label>
                            <button
                                type="button"
                                onClick={() => onChange('')}
                                className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-black text-slate-500 transition hover:text-red-500 dark:border-white/10 dark:bg-white/5 dark:text-slate-300"
                            >
                                Clear
                            </button>
                            <button
                                type="button"
                                onClick={() => setIsOpen(false)}
                                className="rounded-xl bg-modtale-accent px-3 py-2 text-xs font-black text-white transition hover:bg-modtale-accentHover"
                            >
                                Done
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export const StatusIncidents: React.FC<StatusIncidentsProps> = ({ setStatus, canManage = false }) => {
    const [incidents, setIncidents] = useState<StatusIncident[]>([]);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [createForm, setCreateForm] = useState({
        kind: 'MAINTENANCE' as IncidentKind,
        title: '',
        impact: 'DEGRADED' as SystemStatus,
        state: 'SCHEDULED' as IncidentState,
        affectedServices: 'api,database,storage',
        scheduledStart: '',
        scheduledEnd: '',
        message: '',
    });
    const [updateForm, setUpdateForm] = useState({
        incidentId: '',
        state: 'IDENTIFIED' as IncidentState,
        impact: 'DEGRADED' as SystemStatus,
        scheduledEnd: '',
        message: '',
    });

    const activeIncidents = useMemo(() => incidents.filter((incident) => !['RESOLVED', 'CANCELED'].includes(incident.state)), [incidents]);
    const scheduledMaintenances = useMemo(() => activeIncidents.filter((incident) => incident.kind === 'MAINTENANCE' && incident.state === 'SCHEDULED'), [activeIncidents]);
    const history = useMemo(() => incidents.filter((incident) => ['RESOLVED', 'CANCELED'].includes(incident.state)), [incidents]);

    const loadIncidents = async () => {
        setLoading(true);
        try {
            const data = await adminClient.getStatusIncidents();
            setIncidents(data);
            setUpdateForm((previous) => ({
                ...previous,
                incidentId: previous.incidentId || data.find((incident: StatusIncident) => !['RESOLVED', 'CANCELED'].includes(incident.state))?.id || '',
            }));
        } catch (error) {
            setStatus({ type: 'error', title: 'Status Incidents', msg: extractApiErrorMessage(error, 'Could not load status incidents.') });
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadIncidents();
    }, []);

    const payloadDates = (value: string) => normalizeDateTimeValue(value);

    const handleCreate = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!canManage) return;
        setSubmitting(true);
        try {
            await adminClient.createStatusIncident({
                ...createForm,
                affectedServices: createForm.affectedServices.split(',').map((service) => service.trim()).filter(Boolean),
                scheduledStart: payloadDates(createForm.scheduledStart),
                scheduledEnd: payloadDates(createForm.scheduledEnd),
            });
            setStatus({ type: 'success', title: 'Status Created', msg: 'The status entry is live.' });
            setCreateForm((previous) => ({ ...previous, title: '', message: '' }));
            await loadIncidents();
        } catch (error) {
            setStatus({ type: 'error', title: 'Status Create Failed', msg: extractApiErrorMessage(error, 'Could not create this status entry.') });
        } finally {
            setSubmitting(false);
        }
    };

    const handleUpdate = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!canManage) return;
        if (!updateForm.incidentId) return;
        setSubmitting(true);
        try {
            await adminClient.updateStatusIncident(updateForm.incidentId, {
                state: updateForm.state,
                impact: updateForm.impact,
                scheduledEnd: payloadDates(updateForm.scheduledEnd),
                message: updateForm.message,
            });
            setStatus({ type: 'success', title: 'Update Posted', msg: 'The incident timeline has been updated.' });
            setUpdateForm((previous) => ({ ...previous, message: '' }));
            await loadIncidents();
        } catch (error) {
            setStatus({ type: 'error', title: 'Update Failed', msg: extractApiErrorMessage(error, 'Could not post this incident update.') });
        } finally {
            setSubmitting(false);
        }
    };

    const IncidentRow = ({ incident }: { incident: StatusIncident }) => (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-white/10 dark:bg-white/[0.03]">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className={`rounded-full border px-2.5 py-1 text-[11px] font-black uppercase tracking-wider ${stateClass(incident.state)}`}>
                            {stateLabel(incident.state)}
                        </span>
                        <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-black uppercase tracking-wider text-slate-500 dark:border-white/10 dark:bg-white/5 dark:text-slate-400">
                            {incident.kind === 'MAINTENANCE' ? 'Maintenance' : 'Incident'}
                        </span>
                    </div>
                    <h3 className="text-base font-black text-slate-900 dark:text-white">{incident.title}</h3>
                    <p className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">
                        Impact {impactLabel(incident.impact)} · Updated {formatDate(incident.updatedAt)}
                    </p>
                </div>
                <div className="text-xs font-bold text-slate-500 dark:text-slate-400 sm:text-right">
                    <div>{incident.scheduledStart ? `Starts ${formatDate(incident.scheduledStart)}` : `Opened ${formatDate(incident.createdAt)}`}</div>
                    <div>{incident.scheduledEnd ? `Ends ${formatDate(incident.scheduledEnd)}` : 'No end set'}</div>
                </div>
            </div>
            {incident.updates?.[0] && (
                <p className="mt-3 border-t border-slate-200 pt-3 text-sm leading-6 text-slate-600 dark:border-white/10 dark:text-slate-300">
                    {incident.updates[0].message}
                </p>
            )}
        </div>
    );

    return (
        <div className="space-y-8">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h1 className="text-3xl font-black tracking-tight text-slate-900 dark:text-white">Status Management</h1>
                    <p className="mt-1 font-medium text-slate-500 dark:text-slate-400">Schedule downtime, publish incident updates, and review resolved history.</p>
                </div>
                <button
                    type="button"
                    onClick={loadIncidents}
                    disabled={loading}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition hover:border-modtale-accent dark:border-white/10 dark:bg-white/5 dark:text-slate-200"
                >
                    <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                    Refresh
                </button>
            </div>

            {canManage && (
            <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                <form onSubmit={handleCreate} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-slate-950/30">
                    <div className="mb-5 flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-modtale-accent/10 text-modtale-accent">
                            <CalendarClock className="h-5 w-5" />
                        </div>
                        <div>
                            <h2 className="text-lg font-black text-slate-900 dark:text-white">Create Status Entry</h2>
                            <p className="text-sm text-slate-500 dark:text-slate-400">Use maintenance for scheduled downtime.</p>
                        </div>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <label className="space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                            Kind
                            <select className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-bold text-slate-900 dark:border-white/10 dark:bg-black/20 dark:text-white" value={createForm.kind} onChange={(event) => setCreateForm({ ...createForm, kind: event.target.value as IncidentKind, state: event.target.value === 'MAINTENANCE' ? 'SCHEDULED' : 'INVESTIGATING' })}>
                                <option value="MAINTENANCE">Maintenance</option>
                                <option value="INCIDENT">Incident</option>
                            </select>
                        </label>
                        <label className="space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                            Impact
                            <select className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-bold text-slate-900 dark:border-white/10 dark:bg-black/20 dark:text-white" value={createForm.impact} onChange={(event) => setCreateForm({ ...createForm, impact: event.target.value as SystemStatus })}>
                                {impacts.map((impact) => <option key={impact} value={impact}>{impactLabel(impact)}</option>)}
                            </select>
                        </label>
                    </div>

                    <label className="mt-4 block space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                        Title
                        <input required className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none focus:border-modtale-accent dark:border-white/10 dark:bg-black/20 dark:text-white" value={createForm.title} onChange={(event) => setCreateForm({ ...createForm, title: event.target.value })} />
                    </label>

                    <div className="mt-4 grid gap-4 sm:grid-cols-2">
                        <StatusDateTimePicker
                            label="Start"
                            value={createForm.scheduledStart}
                            onChange={(scheduledStart) => setCreateForm({ ...createForm, scheduledStart })}
                            placeholder="Pick start window"
                        />
                        <StatusDateTimePicker
                            label="End"
                            value={createForm.scheduledEnd}
                            onChange={(scheduledEnd) => setCreateForm({ ...createForm, scheduledEnd })}
                            placeholder="Pick end window"
                        />
                    </div>

                    <div className="mt-4">
                        <div className="mb-2 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">Affected Services</div>
                        <div className="flex flex-wrap gap-2">
                            {serviceOptions.map((service) => {
                                const selected = createForm.affectedServices.split(',').map((item) => item.trim()).includes(service);
                                return (
                                    <button
                                        type="button"
                                        key={service}
                                        onClick={() => {
                                            const current = new Set(createForm.affectedServices.split(',').map((item) => item.trim()).filter(Boolean));
                                            if (current.has(service)) current.delete(service);
                                            else current.add(service);
                                            setCreateForm({ ...createForm, affectedServices: Array.from(current).join(',') });
                                        }}
                                        className={`rounded-xl border px-3 py-2 text-sm font-bold transition ${selected ? 'border-modtale-accent bg-modtale-accent/10 text-modtale-accent' : 'border-slate-200 bg-slate-50 text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300'}`}
                                    >
                                        {service}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    <label className="mt-4 block space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                        Initial Update
                        <textarea required rows={4} className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none focus:border-modtale-accent dark:border-white/10 dark:bg-black/20 dark:text-white" value={createForm.message} onChange={(event) => setCreateForm({ ...createForm, message: event.target.value })} />
                    </label>

                    <button disabled={submitting} className="mt-5 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-modtale-accent px-4 text-sm font-bold text-white transition hover:bg-modtale-accentHover disabled:opacity-60">
                        <Megaphone className="h-4 w-4" />
                        Publish Status Entry
                    </button>
                </form>

                <form onSubmit={handleUpdate} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-slate-950/30">
                    <div className="mb-5 flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/10 text-amber-500">
                            <Activity className="h-5 w-5" />
                        </div>
                        <div>
                            <h2 className="text-lg font-black text-slate-900 dark:text-white">Post Current Update</h2>
                            <p className="text-sm text-slate-500 dark:text-slate-400">Add timeline notes as downtime moves along.</p>
                        </div>
                    </div>

                    <label className="block space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                        Incident
                        <select required className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-bold text-slate-900 dark:border-white/10 dark:bg-black/20 dark:text-white" value={updateForm.incidentId} onChange={(event) => setUpdateForm({ ...updateForm, incidentId: event.target.value })}>
                            <option value="">Select an active entry</option>
                            {activeIncidents.map((incident) => <option key={incident.id} value={incident.id}>{incident.title}</option>)}
                        </select>
                    </label>

                    <div className="mt-4 grid gap-4 sm:grid-cols-2">
                        <label className="space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                            State
                            <select className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-bold text-slate-900 dark:border-white/10 dark:bg-black/20 dark:text-white" value={updateForm.state} onChange={(event) => setUpdateForm({ ...updateForm, state: event.target.value as IncidentState })}>
                                {states.map((state) => <option key={state} value={state}>{stateLabel(state)}</option>)}
                            </select>
                        </label>
                        <label className="space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                            Impact
                            <select className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-bold text-slate-900 dark:border-white/10 dark:bg-black/20 dark:text-white" value={updateForm.impact} onChange={(event) => setUpdateForm({ ...updateForm, impact: event.target.value as SystemStatus })}>
                                {impacts.map((impact) => <option key={impact} value={impact}>{impactLabel(impact)}</option>)}
                            </select>
                        </label>
                    </div>

                    <div className="mt-4">
                        <StatusDateTimePicker
                            label="Revised End"
                            value={updateForm.scheduledEnd}
                            onChange={(scheduledEnd) => setUpdateForm({ ...updateForm, scheduledEnd })}
                            placeholder="Pick revised end"
                        />
                    </div>

                    <label className="mt-4 block space-y-1.5 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                        Update
                        <textarea required rows={5} className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none focus:border-modtale-accent dark:border-white/10 dark:bg-black/20 dark:text-white" value={updateForm.message} onChange={(event) => setUpdateForm({ ...updateForm, message: event.target.value })} />
                    </label>

                    <button disabled={submitting || !updateForm.incidentId} className="mt-5 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-modtale-accent px-4 text-sm font-bold text-white transition hover:bg-modtale-accentHover disabled:opacity-60">
                        <Send className="h-4 w-4" />
                        Post Update
                    </button>
                </form>
            </div>
            )}

            <section className="grid gap-6 xl:grid-cols-3">
                <div className="space-y-3">
                    <h2 className="flex items-center gap-2 text-lg font-black text-slate-900 dark:text-white"><Clock3 className="h-5 w-5 text-amber-500" /> Active</h2>
                    {activeIncidents.length ? activeIncidents.map((incident) => <IncidentRow key={incident.id} incident={incident} />) : <EmptyState label="No active incidents." />}
                </div>
                <div className="space-y-3">
                    <h2 className="flex items-center gap-2 text-lg font-black text-slate-900 dark:text-white"><CalendarClock className="h-5 w-5 text-blue-500" /> Scheduled</h2>
                    {scheduledMaintenances.length ? scheduledMaintenances.map((incident) => <IncidentRow key={incident.id} incident={incident} />) : <EmptyState label="No scheduled maintenance." />}
                </div>
                <div className="space-y-3">
                    <h2 className="flex items-center gap-2 text-lg font-black text-slate-900 dark:text-white"><CheckCircle2 className="h-5 w-5 text-emerald-500" /> History</h2>
                    {history.length ? history.slice(0, 8).map((incident) => <IncidentRow key={incident.id} incident={incident} />) : <EmptyState label="No past incidents yet." />}
                </div>
            </section>
        </div>
    );
};

const EmptyState = ({ label }: { label: string }) => (
    <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-5 text-sm font-semibold text-slate-500 dark:border-white/10 dark:bg-white/[0.03] dark:text-slate-400">
        {label}
    </div>
);
