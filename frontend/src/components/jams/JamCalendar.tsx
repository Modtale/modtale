import React from 'react';
import { CalendarDays, ArrowRight, Clock } from 'lucide-react';

interface JamCalendarProps {
    startDate: string;
    endDate: string;
    votingEndDate: string;
    onChange: (field: string, value: string) => void;
}

export const JamCalendar: React.FC<JamCalendarProps> = ({ startDate, endDate, votingEndDate, onChange }) => {
    const today = new Date();
    today.setHours(today.getHours() + 24); // Force at least 24h in future
    const minStart = today.toISOString().slice(0, 16);

    return (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-5 bg-slate-50 dark:bg-black/20 rounded-2xl border border-slate-200 dark:border-white/10 group focus-within:border-modtale-accent transition-all">
                <div className="flex items-center gap-2 mb-3">
                    <CalendarDays className="w-4 h-4 text-modtale-accent" />
                    <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Start Date</span>
                </div>
                <input
                    type="datetime-local"
                    min={minStart}
                    value={startDate}
                    onChange={e => onChange('startDate', e.target.value)}
                    className="w-full bg-transparent border-none outline-none font-bold text-slate-900 dark:text-white color-scheme-dark"
                />
            </div>

            <div className="p-5 bg-slate-50 dark:bg-black/20 rounded-2xl border border-slate-200 dark:border-white/10 group focus-within:border-modtale-accent transition-all">
                <div className="flex items-center gap-2 mb-3">
                    <ArrowRight className="w-4 h-4 text-modtale-accent" />
                    <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Submissions End</span>
                </div>
                <input
                    type="datetime-local"
                    min={startDate}
                    value={endDate}
                    onChange={e => onChange('endDate', e.target.value)}
                    className="w-full bg-transparent border-none outline-none font-bold text-slate-900 dark:text-white color-scheme-dark"
                />
            </div>

            <div className="p-5 bg-slate-50 dark:bg-black/20 rounded-2xl border border-slate-200 dark:border-white/10 group focus-within:border-modtale-accent transition-all">
                <div className="flex items-center gap-2 mb-3">
                    <Clock className="w-4 h-4 text-modtale-accent" />
                    <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Voting Ends</span>
                </div>
                <input
                    type="datetime-local"
                    min={endDate}
                    value={votingEndDate}
                    onChange={e => onChange('votingEndDate', e.target.value)}
                    className="w-full bg-transparent border-none outline-none font-bold text-slate-900 dark:text-white color-scheme-dark"
                />
            </div>
        </div>
    );
};