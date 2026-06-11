import React, {useEffect, useRef} from 'react';
import { Check, AlertTriangle, ArrowRight, X, Info } from 'lucide-react';
import { theme } from '@/styles/theme';

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
    message: React.ReactNode;
    onClose: () => void;
    actionLabel?: string;
    onAction?: () => void;
    secondaryLabel?: string;
}

export const StatusModal: React.FC<StatusModalProps> = ({ type, title, message, onClose, actionLabel, onAction, secondaryLabel }) => {
    const config = {
        success: {
            icon: <Check className="w-8 h-8" />,
            shell: 'border-modtale-accent/30 shadow-[0_40px_120px_-45px_rgba(37,99,235,0.6)]',
            hero: 'from-modtale-accent/18 via-sky-500/10 to-transparent dark:from-modtale-accent/18 dark:via-sky-500/10 dark:to-transparent',
            badge: 'bg-modtale-accent text-white',
            button: theme.components.buttonPrimary,
        },
        error: {
            icon: <AlertTriangle className="w-8 h-8" />,
            shell: 'border-red-500/25 shadow-[0_40px_120px_-45px_rgba(239,68,68,0.55)]',
            hero: 'from-red-500/16 via-rose-500/10 to-transparent dark:from-red-500/20 dark:via-rose-500/10 dark:to-transparent',
            badge: 'bg-red-500 text-white',
            button: theme.components.buttonDanger,
        },
        warning: {
            icon: <AlertTriangle className="w-8 h-8" />,
            shell: 'border-amber-400/30 shadow-[0_40px_120px_-45px_rgba(245,158,11,0.55)]',
            hero: 'from-amber-400/20 via-orange-400/10 to-transparent dark:from-amber-500/20 dark:via-orange-500/10 dark:to-transparent',
            badge: 'bg-amber-500 text-white',
            button: 'bg-amber-500 hover:bg-amber-600 text-white px-5 py-2.5 rounded-xl font-bold transition-all shadow-md active:scale-95 flex items-center justify-center gap-2 disabled:opacity-50 disabled:pointer-events-none',
        },
        info: {
            icon: <Info className="w-8 h-8" />,
            shell: 'border-sky-500/25 shadow-[0_40px_120px_-45px_rgba(14,165,233,0.5)]',
            hero: 'from-sky-500/16 via-blue-500/10 to-transparent dark:from-sky-500/18 dark:via-blue-500/10 dark:to-transparent',
            badge: 'bg-sky-500 text-white',
            button: 'bg-sky-600 hover:bg-sky-700 text-white px-5 py-2.5 rounded-xl font-bold transition-all shadow-md active:scale-95 flex items-center justify-center gap-2 disabled:opacity-50 disabled:pointer-events-none',
        }
    }[type];

    return (
        <div className={`${theme.components.modalOverlay} z-[100]`}>
            {type === 'success' && <Confetti />}
            <div className={`relative z-[110] w-full max-w-xl overflow-hidden rounded-[28px] border bg-white/95 dark:bg-slate-950/95 backdrop-blur-xl ${config.shell}`}>
                <button onClick={onClose} className={`absolute right-4 top-4 z-10 ${theme.components.iconButton}`}>
                    <X className="w-5 h-5" />
                </button>
                <div className={`bg-gradient-to-br ${config.hero} px-6 pb-6 pt-8 text-center`}>
                    <div className={`mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-[1.35rem] shadow-lg ${config.badge}`}>
                        {config.icon}
                    </div>
                    <h2 className="mb-2 text-2xl font-black tracking-tight text-slate-900 dark:text-white">{title}</h2>
                    {typeof message === 'string' ? (
                        <p className="mx-auto max-w-md whitespace-pre-line text-left leading-relaxed text-slate-600 dark:text-slate-300">
                            {message}
                        </p>
                    ) : (
                        <div className="mx-auto max-w-md text-left leading-relaxed text-slate-600 dark:text-slate-300">
                            {message}
                        </div>
                    )}
                </div>
                <div className="flex flex-col-reverse justify-center gap-3 border-t border-slate-200/70 bg-slate-50/85 p-4 dark:border-white/10 dark:bg-white/5 sm:flex-row">
                    {(type === 'warning' || type === 'info') && (
                        <button
                            type="button"
                            onClick={onClose}
                            className={`${theme.components.buttonGhost} justify-center`}
                        >
                            {secondaryLabel || "Cancel"}
                        </button>
                    )}
                    <button
                        type="button"
                        onClick={() => { if (onAction) onAction(); else onClose(); }}
                        className={config.button}
                    >
                        {actionLabel || "Close"}
                        {type === 'success' && <ArrowRight className="w-5 h-5" />}
                    </button>
                </div>
            </div>
        </div>
    );
};
