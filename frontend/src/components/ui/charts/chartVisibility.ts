import { useCallback, useState } from 'react';

export type ChartToggleHandler = (seriesId: string, hidden: boolean) => void;

export const chartVisibilityKey = (chartId: string, seriesId: string) =>
    `${encodeURIComponent(chartId)}::${encodeURIComponent(seriesId)}`;

export const useChartVisibility = () => {
    const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});

    const isHidden = useCallback((chartId: string, seriesId: string, defaultHidden = false) => {
        return hiddenSeries[chartVisibilityKey(chartId, seriesId)] ?? defaultHidden;
    }, [hiddenSeries]);

    const setSeriesHidden = useCallback((chartId: string, seriesId: string, hidden: boolean) => {
        setHiddenSeries(prev => ({
            ...prev,
            [chartVisibilityKey(chartId, seriesId)]: hidden
        }));
    }, []);

    const toggleHandler = useCallback((chartId: string): ChartToggleHandler => {
        return (seriesId, hidden) => setSeriesHidden(chartId, seriesId, hidden);
    }, [setSeriesHidden]);

    return { isHidden, setSeriesHidden, toggleHandler };
};
