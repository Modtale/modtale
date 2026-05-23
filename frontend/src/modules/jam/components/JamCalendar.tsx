import React, { useState, useRef, useEffect } from 'react';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon, Clock } from 'lucide-react';

const JamCalendarWidget = ({ selectedDate, onSelect, minDate }: { selectedDate: Date | null, onSelect: (date: Date) => void, minDate?: Date }) => {
    const [viewDate, setViewDate] = useState(selectedDate || new Date());
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();

    const getDaysInMonth = (y: number, m: number) => new Date(y, m + 1, 0).getDate();
    const getFirstDay = (y: number, m: number) => new Date(y, m, 1).getDay();

    const daysInMonth = getDaysInMonth(year, month);
    const startDay = getFirstDay(year, month);
    const today = new Date();
    today.setHours(0,0,0,0);

    const days = [];
    for (let i = 0; i < startDay; i++) days.push(null);
    for (let i = 1; i <= daysInMonth; i++) days.push(i);

    const isDisabled = (d: number) => {
        const checkDate = new Date(year, month, d);
        if (minDate) return checkDate <= minDate;
        return checkDate <= today;
    };

    return (
        <div className="p-4 bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-white/10 shadow-2xl w-64 animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center mb-4">
                <button type="button" onClick={() => setViewDate(new Date(year, month - 1, 1))} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-full"><ChevronLeft className="w-4 h-4" /></button>
                <span className="text-xs font-black uppercase tracking-widest">{viewDate.toLocaleString('default', { month: 'short', year: 'numeric' })}</span>
                <button type="button" onClick={() => setViewDate(new Date(year, month + 1, 1))} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-full"><ChevronRight className="w-4 h-4" /></button>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center mb-2">
                {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(d => <div key={d} className="text-[10px] font-black text-slate-400">{d}</div>)}
            </div>
            <div className="grid grid-cols-7 gap-1">
                {days.map((d, i) => d ? (
                    <button
                        key={i}
                        type="button"
                        disabled={isDisabled(d)}
                        onClick={() => onSelect(new Date(year, month, d))}
                        className={`w-7 h-7 flex items-center justify-center rounded-lg text-[10px] font-bold transition-all ${
                            isDisabled(d) ? 'opacity-20 cursor-not-allowed' :
                                selectedDate?.getDate() === d && selectedDate?.getMonth() === month ? 'bg-modtale-accent text-white shadow-lg' : 'hover:bg-modtale-accent/10 hover:text-modtale-accent'
                        }`}
                    >
                        {d}
                    </button>
                ) : <div key={i} />)}
            </div>
        </div>
    );
};

export const JamDateInput = ({ label, value, onChange, minDate, icon: Icon }: { label: string, value: string, onChange: (v: string) => void, minDate?: string, icon: any }) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);
    const dateValue = value ? new Date(value) : null;

    useEffect(() => {
        const cmd = (e: MouseEvent) => { if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false); };
        document.addEventListener('mousedown', cmd);
        return () => document.removeEventListener('mousedown', cmd);
    }, []);

    return (
        <div className="relative flex-1" ref={containerRef}>
            <label className="block text-[10px] font-black uppercase text-slate-500 tracking-widest mb-2 ml-1">{label}</label>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full flex items-center gap-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl px-4 py-3 text-left hover:border-modtale-accent transition-all"
            >
                <Icon className="w-4 h-4 text-modtale-accent" />
                <span className="text-sm font-bold">{dateValue ? dateValue.toLocaleDateString() : 'Select Date'}</span>
            </button>
            {isOpen && (
                <div className="absolute top-full mt-2 left-0 z-[100]">
                    <JamCalendarWidget
                        selectedDate={dateValue}
                        minDate={minDate ? new Date(minDate) : undefined}
                        onSelect={(d) => {
                            const newDate = new Date(value || d);
                            newDate.setFullYear(d.getFullYear(), d.getMonth(), d.getDate());
                            onChange(newDate.toISOString());
                            setIsOpen(false);
                        }}
                    />
                </div>
            )}
        </div>
    );
};