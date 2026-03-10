import React, { useState, useId } from 'react';

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
    const chartId = useId();

    const activeDatasets = datasets.filter(d => !d.hidden);
    const hasData = activeDatasets.length > 0;

    const allValues = activeDatasets.flatMap(d => d.data.map(p => p.value));
    const rawMax = Math.max(...allValues, 5);
    const rawMin = Math.min(...allValues, 0);

    const hasNegative = allValues.some(v => v < 0);
    const valRange = Math.max(rawMax - (hasNegative ? rawMin : 0), 1);

    const roughStep = Math.max(valRange / 4, 1);
    const stepMagnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
    const normalizedStep = roughStep / stepMagnitude;
    let niceStep = 10;
    if (normalizedStep <= 1) niceStep = 1;
    else if (normalizedStep <= 2) niceStep = 2;
    else if (normalizedStep <= 2.5) niceStep = 2.5;
    else if (normalizedStep <= 5) niceStep = 5;
    const finalStep = niceStep * stepMagnitude;

    const displayMin = hasNegative ? Math.floor(rawMin / finalStep) * finalStep : 0;
    const displayMax = Math.ceil(rawMax / finalStep) * finalStep;
    const displayRange = Math.max(displayMax - displayMin, 1);

    const ticks = [];
    for (let i = displayMin; i <= displayMax; i += finalStep) {
        ticks.push(i);
    }

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
        const rawY = height - paddingBottom - ((value - displayMin) / displayRange) * chartHeight;
        return Math.max(paddingTop, Math.min(height - paddingBottom, rawY));
    };

    const makePath = (data: DataPoint[]) => {
        if (data.length === 0) return '';
        if (data.length === 1) return `M ${getX(0)} ${getY(data[0].value)}`;

        let path = `M ${getX(0)} ${getY(data[0].value)}`;
        for (let i = 0; i < data.length - 1; i++) {
            const x0 = getX(i);
            const y0 = getY(data[i].value);
            const x1 = getX(i + 1);
            const y1 = getY(data[i + 1].value);
            const cpX1 = x0 + (x1 - x0) / 2.5;
            const cpX2 = x1 - (x1 - x0) / 2.5;
            path += ` C ${cpX1} ${y0}, ${cpX2} ${y1}, ${x1} ${y1}`;
        }
        return path;
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
            <div className="flex flex-wrap gap-2 mb-6 flex-shrink-0">
                {datasets.map(d => (
                    <button
                        key={d.id}
                        onClick={() => onToggle && onToggle(d.id)}
                        className={`flex items-center gap-2 text-[11px] font-bold px-3 py-1.5 rounded-full transition-all border ${
                            d.hidden
                                ? 'bg-transparent text-slate-400 border-slate-300 dark:border-white/10 border-dashed hover:border-slate-400 dark:hover:border-white/20'
                                : 'bg-white dark:bg-white/10 border-slate-200 dark:border-white/5 text-slate-700 dark:text-slate-200 shadow-sm hover:border-modtale-accent'
                        }`}
                    >
                        <span className={`w-2 h-2 rounded-full ${d.hidden ? 'bg-slate-200 dark:bg-slate-700' : 'shadow-sm'}`} style={{ backgroundColor: d.hidden ? undefined : d.color }} />
                        {d.label}
                    </button>
                ))}
            </div>

            {!hasData ? (
                <div className="flex-1 flex items-center justify-center text-slate-400 font-medium bg-slate-50/50 dark:bg-white/[0.02] rounded-2xl border border-dashed border-slate-200 dark:border-white/10">
                    No data selected. Toggle items above.
                </div>
            ) : (
                <div className="flex flex-1 min-h-0 relative">
                    <div className="w-9 relative h-full shrink-0 mr-3">
                        {ticks.map(val => {
                            const topPerc = (getY(val) / height) * 100;
                            return (
                                <div
                                    key={val}
                                    className="absolute left-0 w-full text-left text-[11px] font-bold text-slate-400 dark:text-slate-500 transform -translate-y-1/2 leading-none"
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
                                {activeDatasets.map(d => (
                                    <linearGradient key={`area-grad-${d.id}`} id={`area-grad-${d.id}`} x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="0%" stopColor={d.color} stopOpacity="0.25" />
                                        <stop offset="100%" stopColor={d.color} stopOpacity="0.0" />
                                    </linearGradient>
                                ))}
                            </defs>

                            <g>
                                {ticks.map(val => {
                                    const y = getY(val);
                                    return (
                                        <line
                                            key={val}
                                            x1={0} y1={y}
                                            x2={width} y2={y}
                                            stroke="currentColor"
                                            className="text-slate-200 dark:text-white/5"
                                            strokeWidth="1"
                                            strokeDasharray={val === 0 ? "" : "3 5"}
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
                                        key={`area-${d.id}`}
                                        d={`${makePath(d.data)} L ${getX(d.data.length - 1)} ${height - paddingBottom} L ${getX(0)} ${height - paddingBottom} Z`}
                                        fill={`url(#area-grad-${d.id})`}
                                        className="transition-opacity duration-300 ease-in-out"
                                        style={{ opacity: hoverIndex !== null ? 0.05 : 1 }}
                                        vectorEffect="non-scaling-stroke"
                                    />
                                ))}

                                {activeDatasets.map(d => (
                                    <path
                                        key={d.id}
                                        d={makePath(d.data)}
                                        fill="none"
                                        stroke={d.color}
                                        strokeWidth={d.id === 'overall' || d.id === 'growth' ? "4" : "3"}
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        className="transition-opacity duration-300 ease-in-out"
                                        style={{ opacity: hoverIndex !== null ? 0.2 : 1 }}
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

                                {hoverIndex !== null && activeDatasets.map(d => {
                                    if (!d.data[hoverIndex]) return null;
                                    return (
                                        <circle
                                            key={`dot-${d.id}`}
                                            cx={getX(hoverIndex)}
                                            cy={getY(d.data[hoverIndex].value)}
                                            r="5"
                                            fill={d.color}
                                            strokeWidth="2.5"
                                            className="stroke-white dark:stroke-slate-900 transition-all duration-200"
                                        />
                                    );
                                })}
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
                                className="absolute top-0 bg-white/95 dark:bg-slate-800/95 border border-slate-200 dark:border-white/10 p-4 rounded-2xl shadow-2xl pointer-events-none z-20 text-xs backdrop-blur-xl min-w-[200px]"
                                style={{
                                    left: hoverIndex > (dataLength / 2) ? 'auto' : `${((getX(hoverIndex) / width) * 100)}%`,
                                    right: hoverIndex > (dataLength / 2) ? `${100 - ((getX(hoverIndex) / width) * 100)}%` : 'auto',
                                    transform: hoverIndex > (dataLength / 2) ? 'translateX(-20px)' : 'translateX(20px)',
                                    marginTop: '20px'
                                }}
                            >
                                <div className="text-slate-500 dark:text-slate-400 mb-3 pb-2 border-b border-slate-100 dark:border-white/5 font-bold uppercase tracking-wider text-[10px]">
                                    {dates[hoverIndex] || 'Unknown Date'}
                                </div>
                                <div className="space-y-2.5">
                                    {activeDatasets
                                        .sort((a, b) => (b.data[hoverIndex]?.value || 0) - (a.data[hoverIndex]?.value || 0))
                                        .map(d => (
                                            <div key={d.id} className="flex items-center justify-between gap-6">
                                                <span className="flex items-center gap-2.5">
                                                    <span className="w-2.5 h-2.5 rounded-full shadow-sm" style={{backgroundColor: d.color}}/>
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