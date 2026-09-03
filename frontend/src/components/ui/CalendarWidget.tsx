import React, { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface CalendarWidgetProps {
    selectedDate: Date | null;
    onSelect: (date: Date) => void;
    allowFutureDates?: boolean;
    minDate?: Date | null;
    maxDate?: Date | null;
    className?: string;
}

const startOfDay = (date: Date) => {
    const copy = new Date(date);
    copy.setHours(0, 0, 0, 0);
    return copy;
};

export const CalendarWidget: React.FC<CalendarWidgetProps> = ({
    selectedDate,
    onSelect,
    allowFutureDates = false,
    minDate = null,
    maxDate = null,
    className = '',
}) => {
    const [viewDate, setViewDate] = useState(selectedDate || new Date());
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const startDay = new Date(year, month, 1).getDay();
    const today = startOfDay(new Date());
    const minimumDate = minDate ? startOfDay(minDate) : null;
    const maximumDate = maxDate ? startOfDay(maxDate) : null;
    const days = [];

    for (let i = 0; i < startDay; i += 1) days.push(null);
    for (let i = 1; i <= daysInMonth; i += 1) days.push(i);

    useEffect(() => {
        if (selectedDate) {
            setViewDate(selectedDate);
        }
    }, [selectedDate]);

    const isSelected = (day: number) => (
        !!selectedDate
        && selectedDate.getDate() === day
        && selectedDate.getMonth() === month
        && selectedDate.getFullYear() === year
    );

    const isDisabled = (day: number) => {
        const checkDate = startOfDay(new Date(year, month, day));
        if (!allowFutureDates && checkDate > today) return true;
        if (minimumDate && checkDate < minimumDate) return true;
        if (maximumDate && checkDate > maximumDate) return true;
        return false;
    };

    const canGoNext = () => {
        if (allowFutureDates && !maximumDate) return true;
        const nextMonth = startOfDay(new Date(year, month + 1, 1));
        const limit = maximumDate || today;
        return nextMonth <= new Date(limit.getFullYear(), limit.getMonth(), 1);
    };

    return (
        <div className={`p-3 bg-slate-50 dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-white/10 animate-in fade-in zoom-in-95 duration-200 shadow-sm ${className}`}>
            <div className="flex justify-between items-center mb-3">
                <button type="button" onClick={() => setViewDate(new Date(year, month - 1, 1))} aria-label="Previous month" className="p-1 hover:bg-slate-200 dark:hover:bg-white/[0.05] rounded-full transition-colors flex items-center justify-center">
                    <ChevronLeft className="w-4 h-4 text-slate-500" />
                </button>
                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{viewDate.toLocaleString('default', { month: 'long', year: 'numeric' })}</span>
                <button type="button" onClick={() => setViewDate(new Date(year, month + 1, 1))} disabled={!canGoNext()} aria-label="Next month" className="p-1 hover:bg-slate-200 dark:hover:bg-white/[0.05] rounded-full transition-colors disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center">
                    <ChevronRight className="w-4 h-4 text-slate-500" />
                </button>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center mb-2">
                {['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map((day) => (
                    <div key={day} className="text-[10px] font-bold text-slate-400 uppercase">{day}</div>
                ))}
            </div>
            <div className="grid grid-cols-7 gap-1">
                {days.map((day, index) => (
                    day ? (
                        <button
                            type="button"
                            key={`${year}-${month}-${day}`}
                            disabled={isDisabled(day)}
                            onClick={() => !isDisabled(day) && onSelect(new Date(year, month, day))}
                            aria-label={new Date(year, month, day).toDateString()}
                            className={`w-7 h-7 flex items-center justify-center rounded-lg text-xs font-medium transition-all ${
                                isDisabled(day)
                                    ? 'text-slate-300 dark:text-slate-600 cursor-not-allowed opacity-40'
                                    : isSelected(day)
                                        ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/30'
                                        : 'text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/[0.05]'
                            }`}
                        >
                            {day}
                        </button>
                    ) : <div key={`blank-${index}`} />
                ))}
            </div>
        </div>
    );
};
