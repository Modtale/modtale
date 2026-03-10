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
    const roughStep = Math.max(rawMax / 4, 1);
    const stepMagnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
    const normalizedStep = roughStep / stepMagnitude;
    let niceStep = 10;
    if (normalizedStep <= 1) niceStep = 1;
    else if (normalizedStep <= 2) niceStep = 2;
    else if (normalizedStep <= 2.5) niceStep = 2.5;
    else if (normalizedStep <= 5) niceStep = 5;
    const finalStep = niceStep * stepMagnitude;

    const displayMax = Math.ceil(rawMax / finalStep) * finalStep;
    const ticks = [];
    for (let i = 0; i <= displayMax; i += finalStep) {
        ticks.push(i);
    }

    const count = activeData.length;
    const barWidth = count > 0 ? Math.min(100, (chartWidth / count) * 0.7) : 0;
    const gap = count > 1 ? (chartWidth - (barWidth * count)) / (count - 1) : 0;

    const startX = count === 1 ? paddingX + (chartWidth - barWidth) / 2 : paddingX;

    const getY = (val: number) => {
        const rawY = height - paddingBottom - ((val / displayMax) * chartHeight);
        return Math.max(paddingTop, Math.min(height - paddingBottom, rawY));
    };

    const formatY = (val: number) => {
        if (formatter) return formatter(val);
        if (val >= 1000) return (val / 1000).toFixed(1) + 'k';
        return Math.round(val).toLocaleString();
    };

    return (
        <div className="w-full select-none h-full flex flex-col relative">
            <div className="flex flex-wrap gap-2 mb-6 flex-shrink-0">
                {data.map(d => (
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

                    <div className="flex-1 relative h-full w-full overflow-visible">
                        <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="w-full h-full block overflow-visible" onMouseLeave={() => setHoverIndex(null)}>
                            <defs>
                                {activeData.map(d => (
                                    <linearGradient key={`grad-${d.id}`} id={`grad-${d.id}`} x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="0%" stopColor={d.color} stopOpacity="1" />
                                        <stop offset="100%" stopColor={d.color} stopOpacity="0.5" />
                                    </linearGradient>
                                ))}
                                <clipPath id={`grid-clip-${chartId}`}>
                                    <rect x={paddingX} y={paddingTop} width={chartWidth} height={chartHeight} />
                                </clipPath>
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
                                            strokeDasharray="4 6"
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
                                        <g key={d.id}>
                                            <rect
                                                x={x}
                                                y={y}
                                                width={barWidth}
                                                height={Math.max(0, barH + 10)}
                                                fill={`url(#grad-${d.id})`}
                                                className="transition-opacity duration-300 ease-out"
                                                style={{ opacity: hoverIndex !== null && hoverIndex !== i ? 0.3 : 1 }}
                                                rx={8}
                                                vectorEffect="non-scaling-stroke"
                                            />
                                            <rect
                                                x={x - (gap / 2)}
                                                y={paddingTop}
                                                width={barWidth + gap}
                                                height={chartHeight}
                                                fill="transparent"
                                                onMouseEnter={() => setHoverIndex(i)}
                                            />
                                        </g>
                                    );
                                })}
                            </g>
                        </svg>

                        {hoverIndex !== null && activeData[hoverIndex] && (
                            <div
                                className="absolute bg-white/95 dark:bg-slate-800/95 border border-slate-200 dark:border-white/10 p-4 rounded-2xl shadow-2xl text-xs z-50 pointer-events-none transform -translate-x-1/2 -translate-y-full min-w-[160px] text-center backdrop-blur-xl"
                                style={{
                                    left: `${(( (count === 1 ? startX : paddingX + (hoverIndex * (barWidth + gap))) + barWidth/2 ) / width) * 100}%`,
                                    top: `${(getY(activeData[hoverIndex].value) / height) * 100}%`,
                                    marginTop: '-16px'
                                }}
                            >
                                <div className="font-bold text-slate-500 dark:text-slate-400 mb-2 pb-2 border-b border-slate-100 dark:border-white/5 tracking-wider uppercase text-[10px]">{activeData[hoverIndex].label}</div>
                                <div className="font-mono text-2xl font-bold text-slate-900 dark:text-white flex items-center justify-center gap-2">
                                    <span className="w-3 h-3 rounded-full shadow-sm" style={{ backgroundColor: activeData[hoverIndex].color }} />
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