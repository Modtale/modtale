export const COLORS = ['#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16', '#6366f1', '#d946ef'];
export const OVERALL_COLOR = '#6366f1';
export const BUFFER = 14;

export const sliceData = (arr: any[]) => arr.slice(BUFFER);

export const calculateWoW = (fullData: any[]) => fullData.map((p, i, arr) => {
    if (i < 13) return { date: p.date, value: 0 };
    const curr = arr.slice(i - 6, i + 1).reduce((a: number, x: any) => a + x.value, 0);
    const prev = arr.slice(i - 13, i - 6).reduce((a: number, x: any) => a + x.value, 0);
    return { date: p.date, value: prev > 0 ? ((curr - prev) / prev) * 100 : (curr > 0 ? 100 : 0) };
});

export const generateEmptyHistory = (days: number, start: Date) => {
    const l = [];
    const current = new Date(start);

    for (let i = 0; i <= days; i++) {
        const dateStr = current.toISOString().split('T')[0];
        l.push({ date: dateStr, value: 0, count: 0 });
        current.setDate(current.getDate() + 1);
    }
    return l;
};