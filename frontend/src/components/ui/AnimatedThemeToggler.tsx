import { useCallback, useEffect, useRef, useState } from 'react';
import { Moon, Sun } from 'lucide-react';
import { flushSync } from 'react-dom';

interface AnimatedThemeTogglerProps extends React.ComponentPropsWithoutRef<'button'> {
    duration?: number;
    onToggle?: () => void;
}

export const AnimatedThemeToggler = ({
    className,
    duration = 400,
    onToggle,
    ...props
}: AnimatedThemeTogglerProps) => {
    const [isDark, setIsDark] = useState(true);
    const buttonRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        const updateTheme = () => {
            setIsDark(document.documentElement.classList.contains('dark'));
        };

        updateTheme();

        const observer = new MutationObserver(updateTheme);
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['class'],
        });

        return () => observer.disconnect();
    }, []);

    const toggleTheme = useCallback(async () => {
        if (!buttonRef.current) return;

        const supportsViewTransition = 'startViewTransition' in document;

        if (supportsViewTransition) {
            await (document as any).startViewTransition(() => {
                flushSync(() => {
                    const newTheme = !isDark;
                    setIsDark(newTheme);
                    document.documentElement.classList.toggle('dark');
                    localStorage.setItem('modtale-theme', newTheme ? 'dark' : 'light');
                    onToggle?.();
                });
            }).ready;

            const { top, left, width, height } = buttonRef.current.getBoundingClientRect();
            const x = left + width / 2;
            const y = top + height / 2;
            const maxRadius = Math.hypot(
                Math.max(left, window.innerWidth - left),
                Math.max(top, window.innerHeight - top)
            );

            document.documentElement.animate(
                {
                    clipPath: [
                        `circle(0px at ${x}px ${y}px)`,
                        `circle(${maxRadius}px at ${x}px ${y}px)`,
                    ],
                },
                {
                    duration,
                    easing: 'ease-in-out',
                    pseudoElement: '::view-transition-new(root)',
                }
            );
        } else {
            const newTheme = !isDark;
            setIsDark(newTheme);
            document.documentElement.classList.toggle('dark');
            localStorage.setItem('modtale-theme', newTheme ? 'dark' : 'light');
            onToggle?.();
        }
    }, [isDark, duration, onToggle]);

    return (
        <button
            ref={buttonRef}
            onClick={toggleTheme}
            className={className}
            {...props}
        >
            {isDark ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
            <span className="sr-only">Toggle theme</span>
        </button>
    );
};
