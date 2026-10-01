const SELECTORS = new Set(['h1', 'h2', 'h3', 'p', 'a', 'blockquote', 'ul', 'ol', 'li', 'code', 'pre', 'table', 'th', 'td', 'hr', 'strong', 'em']);
const PROPERTIES = new Set(['background-color', 'color', 'border-color', 'border-radius', 'font-size', 'font-weight', 'line-height', 'letter-spacing', 'padding', 'margin', 'text-align', 'text-decoration']);

// Deliberately small CSS grammar: no selectors outside jam Markdown, resource
// loads, at-rules, escapes, overlays, animations, or document-level effects.
export const getScopedJamCss = (css?: string | null): string => {
    if (!css?.trim()) return '';
    if (css.length > 10000 || /[@\\<>"'!]/.test(css)) return '';
    const input = css.replace(/\/\*[\s\S]*?\*\//g, '').trim();
    const rules = [...input.matchAll(/([^{}]+)\{([^{}]*)\}/g)];
    if (!rules.length || rules.map(rule => rule[0]).join('').replace(/\s/g, '') !== input.replace(/\s/g, '')) return '';
    const result: string[] = [];
    for (const rule of rules) {
        const selectors = rule[1].split(',').map(value => value.trim());
        if (selectors.some(value => !SELECTORS.has(value))) return '';
        const declarations: string[] = [];
        for (const declaration of rule[2].split(';').filter(value => value.trim())) {
            const parts = declaration.split(':');
            if (parts.length !== 2) return '';
            const property = parts[0].trim();
            const value = parts[1].trim();
            if (!PROPERTIES.has(property) || !value || !/^[a-zA-Z0-9#.%(),\s-]+$/.test(value) || /\b(?:url|expression|var|attr|env)\s*\(/i.test(value)) return '';
            if (/[()]/.test(value) && !/^(?:rgb|rgba|hsl|hsla)\([\d.%\s,/-]+\)$/i.test(value)) return '';
            declarations.push(`${property}: ${value}`);
        }
        result.push(`${selectors.map(value => `.jam-custom-content ${value}`).join(', ')} { ${declarations.join('; ')}; }`);
    }
    return result.join('\n');
};
