import React, { useState, useId } from 'react';

interface BarData {
    id: string;
    label: string;
    value: number;
    color: string;
    hidden?: boolean;
}

interface BarChartProps {
    data: BarData[];
    formatter?: (val: number) => string;
    onToggle?: (id: string) => void;
}

export const BarChart: React.FC<BarChartProps> = ({ data, formatter, onToggle }) => {
    const [hoverIndex, setHoverIndex] = useState<number | null>(null);
    const chartId = useId();

    const activeData = data.filter(d => !d.hidden);
    const hasData = activeData.length > 0;

    const width = 1000;
    const height = 400;

    const paddingTop = 20;
    const paddingBottom = 20;
    const paddingX = 10;

    const chartWidth = width - (paddingX * 2);
    const chartHeight = height - paddingTop - paddingBottom;

    const rawMax = Math.max(...activeData.map(d => d.value), 1);
    const maxValue = rawMax * 1.1;

    const count = activeData.length;
    const barWidth = count > 0 ? Math.min(100, (chartWidth / count) * 0.7) : 0;
    const gap = count > 1 ? (chartWidth - (barWidth * count)) / (count - 1) : 0;

    const startX = count === 1 ? paddingX + (chartWidth - barWidth) / 2 : paddingX;

    const getY = (val: number) => {
        const rawY = height - paddingBottom - ((val / maxValue) * chartHeight);
        return Math.max(paddingTop, Math.min(height - paddingBottom, rawY));
    };

    const formatY = (val: number) => {
        if (formatter) return formatter(val);
        if (val >= 1000) return (val / 1000).toFixed(1) + 'k';
        return Math.round(val).toLocaleString();
    };

    return (
        <div className="w-full select-none h-full flex flex-col relative">
            <div className="flex flex-wrap gap-1.5 mb-2 pl-[50px] flex-shrink-0">
                {data.map(d => (
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
                            const val = t * maxValue;
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

                    <div className="flex-1 relative h-full w-full overflow-visible">
                        <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="w-full h-full block overflow-visible">
                            <defs>
                                {activeData.map(d => (
                                    <linearGradient key={`grad-${d.id}`} id={`grad-${d.id}`} x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="0%" stopColor={d.color} stopOpacity="1" />
                                        <stop offset="100%" stopColor={d.color} stopOpacity="0.6" />
                                    </linearGradient>
                                ))}
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
                                            strokeDasharray="4 4"
                                            vectorEffect="non-scaling-stroke"
                                        />
                                    );
                                })}
                            </g>

                            <g clipPath={`url(#grid-clip-${chartId})`}>
                                {activeData.map((d, i) => {
                                    const x = count === 1 ? startX : paddingX + (i * (barWidth + gap));
                                    const y = getY(d.value);
                                    const barH = (height - paddingBottom) - y;

                                    return (
                                        <g
                                            key={d.id}
                                            onMouseEnter={() => setHoverIndex(i)}
                                            onMouseLeave={() => setHoverIndex(null)}
                                        >
                                            <rect
                                                x={x}
                                                y={y}
                                                width={barWidth}
                                                height={Math.max(0, barH)}
                                                fill={`url(#grad-${d.id})`}
                                                className="transition-all duration-200 hover:opacity-80 hover:brightness-110"
                                                rx={4}
                                                vectorEffect="non-scaling-stroke"
                                            />
                                        </g>
                                    );
                                })}
                            </g>
                        </svg>

                        {hoverIndex !== null && activeData[hoverIndex] && (
                            <div
                                className="absolute bg-white/95 dark:bg-slate-800/95 border border-slate-200 dark:border-white/10 p-2 rounded-lg shadow-xl text-xs z-50 pointer-events-none transform -translate-x-1/2 -translate-y-full min-w-[120px] text-center backdrop-blur-md"
                                style={{
                                    left: `${(( (count === 1 ? startX : paddingX + (hoverIndex * (barWidth + gap))) + barWidth/2 ) / width) * 100}%`,
                                    top: `${(getY(activeData[hoverIndex].value) / height) * 100}%`,
                                    marginTop: '-8px'
                                }}
                            >
                                <div className="font-bold text-slate-900 dark:text-white mb-1">{activeData[hoverIndex].label}</div>
                                <div className="font-mono text-lg font-bold text-modtale-accent">
                                    {formatter ? formatter(activeData[hoverIndex].value) : activeData[hoverIndex].value.toLocaleString()}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};