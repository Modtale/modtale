const toValidTimestamp = (value?: string | null): number | null => {
    if (!value) return null;
    const timestamp = new Date(value).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
};

export const getJamMilestoneMinimum = (todayStartMs: number, precedingMilestone?: string): number => {
    const precedingTimestamp = toValidTimestamp(precedingMilestone);
    return precedingTimestamp === null ? todayStartMs : Math.max(todayStartMs, precedingTimestamp);
};

export const getClampedJamMilestoneDate = (value: string | undefined, minimumTimestamp: number): Date => {
    const valueTimestamp = toValidTimestamp(value);
    return new Date(valueTimestamp === null ? minimumTimestamp : Math.max(valueTimestamp, minimumTimestamp));
};
