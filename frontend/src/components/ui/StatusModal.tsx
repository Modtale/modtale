import React, {useEffect, useRef, useState} from 'react';
import { Check, AlertTriangle, ArrowRight, X, Trash2, Info } from 'lucide-react';

interface Particle {
    x: number;
    y: number;
    vx: number;
    vy: number;
    color: string;
    w: number;
    h: number;
    gravity: number;
    drag: number;
    angle: number;
    rotationSpeed: number;
    tilt: number;
    tiltAngleIncrement: number;
    active: boolean;
}

const Confetti: React.FC = () => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const dpr = window.devicePixelRatio || 1;
        canvas.width = window.innerWidth * dpr;
        canvas.height = window.innerHeight * dpr;
        ctx.scale(dpr, dpr);

        const particles: Particle[] = [];
        const particleCount = 400;
        const colors = ['#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#ffffff'];

        for (let i = 0; i < particleCount; i++) {
            const angle = Math.random() * Math.PI * 2;
            const velocity = Math.random() * 35 + 10;
            particles.push({
                x: window.innerWidth / 2,
                y: window.innerHeight / 2,
                vx: Math.cos(angle) * velocity,
                vy: Math.sin(angle) * velocity,
                color: colors[Math.floor(Math.random() * colors.length)],
                w: Math.random() * 12 + 4,
                h: Math.random() * 6 + 4,
                gravity: 0.6,
                drag: 0.92,
                angle: Math.random() * 360,
                rotationSpeed: (Math.random() - 0.5) * 0.3,
                tilt: Math.random() * 10,
                tiltAngleIncrement: Math.random() * 0.1 + 0.05,
                active: true
            });
        }

        let animationFrameId: number;
        const animate = () => {
            ctx.clearRect(0, 0, canvas.width / dpr, canvas.height / dpr);
            let activeCount = 0;

            particles.forEach(p => {
                if (!p.active) return;
                p.x += p.vx;
                p.y += p.vy;
                p.vy += p.gravity;
                p.vx *= p.drag;
                p.vy *= p.drag;
                p.tilt += p.tiltAngleIncrement;
                p.angle += p.rotationSpeed;

                if (p.y > window.innerHeight + 100) {
                    p.active = false;
                } else {
                    activeCount++;
                }

                ctx.save();
                ctx.translate(p.x, p.y);
                ctx.rotate(p.angle);
                ctx.scale(1, Math.cos(p.tilt));
                ctx.fillStyle = p.color;
                ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
                ctx.restore();
            });

            if (activeCount > 0) {
                animationFrameId = requestAnimationFrame(animate);
            }
        };

        animate();
        return () => cancelAnimationFrame(animationFrameId);
    }, []);

    return <canvas ref={canvasRef} className="fixed inset-0 pointer-events-none z-[60] w-full h-full" />;
};

interface StatusModalProps {
    type: 'success' | 'error' | 'warning' | 'info';
    title: string;
    message: string;
    onClose: () => void;
    actionLabel?: string;
    onAction?: () => void;
    secondaryLabel?: string;
}

export const StatusModal: React.FC<StatusModalProps> = ({ type, title, message, onClose, actionLabel, onAction, secondaryLabel }) => {
    let icon = <Check className="w-8 h-8" />;
    let colorClass = 'bg-modtale-accent text-white';
    let bgClass = 'bg-modtale-accent/10';
    let borderClass = 'border-modtale-accent';
    let buttonClass = 'bg-modtale-accent hover:bg-modtale-accentHover';

    if (type === 'error') {
        icon = <AlertTriangle className="w-8 h-8" />;
        colorClass = 'bg-red-500 text-white';
        bgClass = 'bg-red-500/10';
        borderClass = 'border-red-500';
        buttonClass = 'bg-red-500 hover:bg-red-600';
    } else if (type === 'warning') {
        icon = <Trash2 className="w-8 h-8" />;
        colorClass = 'bg-orange-500 text-white';
        bgClass = 'bg-orange-500/10';
        borderClass = 'border-orange-500';
        buttonClass = 'bg-red-500 hover:bg-red-600';
    } else if (type === 'info') {
        icon = <Info className="w-8 h-8" />;
        colorClass = 'bg-blue-500 text-white';
        bgClass = 'bg-blue-500/10';
        borderClass = 'border-blue-500';
        buttonClass = 'bg-blue-600 hover:bg-blue-700';
    }

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
            {type === 'success' && <Confetti />}
            <div className={`bg-white dark:bg-modtale-card border ${borderClass} rounded-xl w-full max-w-md shadow-2xl overflow-hidden relative z-[110]`}>
                <button onClick={onClose} className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:hover:text-white">
                    <X className="w-5 h-5" />
                </button>
                <div className={`p-6 text-center ${bgClass}`}>
                    <div className={`mx-auto w-16 h-16 rounded-full flex items-center justify-center mb-4 ${colorClass}`}>
                        {icon}
                    </div>
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-2">{title}</h2>
                    <p className="text-slate-600 dark:text-slate-300">{message}</p>
                </div>
                <div className="p-4 flex justify-center gap-3">
                    {(type === 'warning' || type === 'info') && (
                        <button
                            onClick={onClose}
                            className="px-6 py-3 rounded-lg font-bold text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors"
                        >
                            {secondaryLabel || "Cancel"}
                        </button>
                    )}
                    <button
                        onClick={() => { if (onAction) onAction(); else onClose(); }}
                        className={`flex items-center gap-2 px-8 py-3 rounded-lg font-bold text-white transition-transform active:scale-95 ${buttonClass}`}
                    >
                        {actionLabel || "Close"}
                        {type === 'success' && <ArrowRight className="w-5 h-5" />}
                    </button>
                </div>
            </div>
        </div>
    );
};