import React, { useState } from 'react';

interface DataPoint {
    date: string;
    value: number;
}

interface Dataset {
    id: string;
    label: string;
    color: string;
    data: DataPoint[];
    hidden?: boolean;
}

interface LineChartProps {
    datasets: Dataset[];
    onToggle?: (id: string) => void;
    yAxisFormatter?: (val: number) => string;
}

export const LineChart: React.FC<LineChartProps> = ({ datasets, onToggle, yAxisFormatter }) => {
    const [hoverIndex, setHoverIndex] = useState<number | null>(null);

    const [chartId] = useState(() => 'chart-' + Math.random().toString(36).substring(2, 9));

    const activeDatasets = datasets.filter(d => !d.hidden);
    const hasData = activeDatasets.length > 0;

    const allValues = activeDatasets.flatMap(d => d.data.map(p => p.value));
    const rawMax = Math.max(...allValues, 5);
    const rawMin = Math.min(...allValues, 0);

    const hasNegative = allValues.some(v => v < 0);
    const range = rawMax - rawMin;
    const buffer = range === 0 ? 1 : range * 0.1;

    const displayMax = rawMax + buffer;
    const displayMin = hasNegative ? rawMin - buffer : 0;

    const dataLength = Math.max(...activeDatasets.map(d => d.data.length), 0);

    const width = 1000;
    const height = 400;

    const paddingTop = 20;
    const paddingBottom = 20;
    const paddingX = 10;

    const chartWidth = width - (paddingX * 2);
    const chartHeight = height - paddingTop - paddingBottom;

    const xStep = dataLength > 1 ? chartWidth / (dataLength - 1) : 0;

    const getX = (index: number) => index * xStep + paddingX;

    const getY = (value: number) => {
        const rawY = height - paddingBottom - ((value - displayMin) / (displayMax - displayMin)) * chartHeight;
        return Math.max(paddingTop, Math.min(height - paddingBottom, rawY));
    };

    const makePath = (data: DataPoint[]) => {
        if (data.length === 0) return '';
        return data.map((p, i) => `${i === 0 ? 'M' : 'L'} ${getX(i)} ${getY(p.value)}`).join(' ');
    };

    const formatY = (val: number) => {
        if (yAxisFormatter) return yAxisFormatter(val);
        if (Math.abs(val) >= 1000) return (val / 1000).toFixed(1) + 'k';
        return Math.round(val).toLocaleString();
    };

    const referenceDataset = activeDatasets.find(d => d.data.length > 0) || activeDatasets[0];
    const dates = referenceDataset?.data.map(d => d.date) || [];
    const zeroY = displayMin < 0 && displayMax > 0 ? getY(0) : null;

    return (
        <div className="w-full select-none h-full flex flex-col relative">
            <div className="flex flex-wrap gap-1.5 mb-2 pl-[50px] flex-shrink-0">
                {datasets.map(d => (
                    <button
                        key={d.id}
                        onClick={() => onToggle && onToggle(d.id)}
                        className={`flex items-center gap-1.5 text-[10px] uppercase font-bold px-2 py-0.5 rounded-md transition-all border ${
                            d.hidden
                                ? 'bg-slate-50 dark:bg-white/5 text-slate-400 border-slate-200 dark:border-white/5'
                                : 'bg-white dark:bg-modtale-card border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 shadow-sm hover:border-modtale-accent'
                        }`}
                    >
                        <span className={`w-2 h-2 rounded-full ${d.hidden ? 'bg-slate-300' : ''}`} style={{ backgroundColor: d.hidden ? undefined : d.color }} />
                        {d.label}
                    </button>
                ))}
            </div>

            {!hasData ? (
                <div className="flex-1 flex items-center justify-center text-slate-400 font-medium bg-slate-50/50 dark:bg-white/[0.02] rounded-xl border border-dashed border-slate-200 dark:border-white/10 ml-[50px]">
                    No data selected. Toggle items above.
                </div>
            ) : (
                <div className="flex flex-1 min-h-0 relative">
                    <div className="w-[50px] relative h-full shrink-0 mr-2">
                        {[0, 0.25, 0.5, 0.75, 1].map(t => {
                            const val = displayMin + t * (displayMax - displayMin);
                            const topPerc = 100 - (((t * chartHeight) + paddingBottom) / height) * 100;

                            return (
                                <div
                                    key={t}
                                    className="absolute right-0 w-full text-right pr-2 text-[10px] font-bold text-slate-400 dark:text-slate-500 transform -translate-y-1/2 leading-none"
                                    style={{ top: `${topPerc}%` }}
                                >
                                    {formatY(val)}
                                </div>
                            );
                        })}
                    </div>

                    <div
                        className="flex-1 relative h-full w-full overflow-hidden"
                        onMouseLeave={() => setHoverIndex(null)}
                    >
                        <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="w-full h-full block">
                            <defs>
                                <clipPath id={`grid-clip-${chartId}`}>
                                    <rect x={paddingX} y={paddingTop} width={chartWidth} height={chartHeight} />
                                </clipPath>
                            </defs>

                            <g>
                                {[0, 0.25, 0.5, 0.75, 1].map(t => {
                                    const y = height - paddingBottom - (t * chartHeight);
                                    return (
                                        <line
                                            key={t}
                                            x1={0} y1={y}
                                            x2={width} y2={y}
                                            stroke="currentColor"
                                            className="text-slate-100 dark:text-white/5"
                                            strokeWidth="1"
                                            strokeDasharray={t === 0 ? "" : "4 4"}
                                            vectorEffect="non-scaling-stroke"
                                        />
                                    );
                                })}
                                {zeroY && (
                                    <line
                                        x1={0} y1={zeroY}
                                        x2={width} y2={zeroY}
                                        stroke="currentColor"
                                        className="text-slate-300 dark:text-white/20"
                                        strokeWidth="2"
                                        vectorEffect="non-scaling-stroke"
                                    />
                                )}
                            </g>

                            <g clipPath={`url(#grid-clip-${chartId})`}>
                                {activeDatasets.map(d => (
                                    <path
                                        key={d.id}
                                        d={makePath(d.data)}
                                        fill="none"
                                        stroke={d.color}
                                        strokeWidth={d.id === 'overall' || d.id === 'growth' ? "4" : "3"}
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        className="transition-all duration-300 ease-in-out"
                                        style={{ opacity: hoverIndex !== null ? 0.3 : 1 }}
                                        vectorEffect="non-scaling-stroke"
                                    />
                                ))}

                                {activeDatasets.map(d => (
                                    <path
                                        key={`highlight-${d.id}`}
                                        d={makePath(d.data)}
                                        fill="none"
                                        stroke={d.color}
                                        strokeWidth={d.id === 'overall' || d.id === 'growth' ? "4" : "3"}
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        className="transition-opacity duration-200"
                                        style={{ opacity: hoverIndex !== null ? 1 : 0 }}
                                        vectorEffect="non-scaling-stroke"
                                    />
                                ))}
                            </g>

                            {hoverIndex !== null && (
                                <line
                                    x1={getX(hoverIndex)} y1={paddingTop}
                                    x2={getX(hoverIndex)} y2={height - paddingBottom}
                                    stroke="currentColor"
                                    className="text-slate-400 dark:text-white/30"
                                    strokeWidth="1.5"
                                    strokeDasharray="4 2"
                                    vectorEffect="non-scaling-stroke"
                                />
                            )}

                            <rect
                                x={0} y={0} width={width} height={height} fill="transparent"
                                onMouseMove={(e) => {
                                    const rect = e.currentTarget.getBoundingClientRect();
                                    const x = e.clientX - rect.left;
                                    const ratio = x / rect.width;
                                    const idx = Math.min(dataLength - 1, Math.max(0, Math.round(ratio * (dataLength - 1))));
                                    setHoverIndex(idx);
                                }}
                            />
                        </svg>

                        {hoverIndex !== null && (
                            <div
                                className="absolute top-0 bg-white/95 dark:bg-slate-800/95 border border-slate-200 dark:border-white/10 p-4 rounded-xl shadow-2xl pointer-events-none z-20 text-xs backdrop-blur-md min-w-[180px]"
                                style={{
                                    left: hoverIndex > (dataLength / 2) ? 'auto' : `${((getX(hoverIndex) / width) * 100)}%`,
                                    right: hoverIndex > (dataLength / 2) ? `${100 - ((getX(hoverIndex) / width) * 100)}%` : 'auto',
                                    transform: hoverIndex > (dataLength / 2) ? 'translateX(-15px)' : 'translateX(15px)',
                                    marginTop: '20px'
                                }}
                            >
                                <div className="text-slate-500 dark:text-slate-400 mb-3 pb-2 border-b border-slate-100 dark:border-white/5 font-bold uppercase tracking-wider text-[10px]">
                                    {dates[hoverIndex] || 'Unknown Date'}
                                </div>
                                <div className="space-y-2">
                                    {activeDatasets
                                        .sort((a, b) => (b.data[hoverIndex]?.value || 0) - (a.data[hoverIndex]?.value || 0))
                                        .map(d => (
                                            <div key={d.id} className="flex items-center justify-between gap-6">
                                            <span className="flex items-center gap-2">
                                                <span className="w-2 h-2 rounded-full" style={{backgroundColor: d.color}}/>
                                                <span className={`font-bold ${d.id === 'overall' || d.id === 'growth' ? 'text-slate-900 dark:text-white' : 'text-slate-600 dark:text-slate-300'}`}>{d.label}</span>
                                            </span>
                                                <span className={`font-mono font-bold ${d.id === 'overall' || d.id === 'growth' ? 'text-lg text-slate-900 dark:text-white' : 'text-slate-700 dark:text-slate-200'}`}>
                                                {yAxisFormatter ? yAxisFormatter(d.data[hoverIndex]?.value || 0) : (d.data[hoverIndex]?.value.toLocaleString() || 0)}
                                            </span>
                                            </div>
                                        ))}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};