// Display rules mirror the launcher's ManifestVersionLabel; never change compatibility data.
type V = [number, number, number];
const parse = (value: string): V | null => /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value)
    ? value.replace(/^v/, '').split('.').map(Number) as V : null;
const cmp = (a: V, b: V) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
const series = (v: V) => `${v[0]}.${v[1]}.x`;
const text = (v: V) => v.join('.');
const words = ([op, v]: string[]) => op === '>=' ? `${v} and newer` : op === '>' ? `After ${v}` : op === '<=' ? `${v} or older` : op === '<' ? `Before ${v}` : v;
function range(lo: V, hi: V, inclusive: boolean): string {
    if (!inclusive && lo[0] === hi[0] && hi[2] === 0 && hi[1] > lo[1] && lo[2] === 0) {
        if (hi[1] - lo[1] > 8) return `${series(lo)} – ${series([hi[0], hi[1] - 1, 0])}`;
        return Array.from({ length: hi[1] - lo[1] }, (_, i) => series([lo[0], lo[1] + i, 0])).join(' & ');
    }
    if (!inclusive && lo[1] === 0 && lo[2] === 0 && hi[0] === lo[0] + 1 && hi[1] === 0 && hi[2] === 0) return `${lo[0]}.x`;
    if (!inclusive && lo[0] === hi[0] && hi[1] === lo[1] + 1 && hi[2] === 0) return `${text(lo)} – ${series(lo)}`;
    if (!inclusive && lo[0] === hi[0] && lo[1] === hi[1] && hi[2] > lo[2]) hi = [hi[0], hi[1], hi[2] - 1];
    else if (!inclusive) return `${text(lo)} – before ${text(hi)}`;
    return cmp(lo, hi) === 0 ? text(lo) : `${text(lo)} – ${text(hi)}`;
}
export function formatManifestVersion(raw: string, knownVersions?: string[]): string {
    const value = raw.trim().replace(/\s+/g, ' ').replace(/(>=|<=|>|<)\s*v?(\d+)(?:\.(\d+))?(?:\.[xX*])?(?=$|[ ,&|])/g, (_, op, major, minor) => {
        let a = Number(major), b = Number(minor || 0);
        if (op === '>' || op === '<=') { if (minor === undefined) a++; else b++; op = op === '>' ? '>=' : '<'; }
        return `${op}${a}.${b}.0`;
    });
    let label = formatValue(value);
    if (knownVersions) {
        const partial = /^(\d+\.\d+\.\d+) – (\d+)\.(\d+)\.x$/.exec(label);
        if (partial) {
            const start = parse(partial[1])!;
            const latest = knownVersions.map(parse).filter((v): v is V => !!v && v[0] === start[0] && v[1] === start[1] && cmp(v, start) >= 0).sort(cmp).at(-1);
            if (latest) label = range(start, latest, true);
        }
        const preview = /^>=\s*(\d+\.\d+\.\d+)-[^ ]+\s+<\s*(\d+\.\d+\.\d+)$/.exec(value);
        if (preview) {
            const lo = parse(preview[1])!, hi = parse(preview[2])!;
            if (lo[2] === 0 && hi[2] === 0 && lo[0] === hi[0] && hi[1] === lo[1] + 1) label = series(lo);
        }
    }
    return label;
}
function formatValue(value: string): string {
    if (!value || value.startsWith('||') || value.endsWith('||')) return value;
    if (value.includes('||')) return [...new Set(value.split(/\s*\|\|\s*/).map(v => formatManifestVersion(v)))].join(' & ');
    if (/^[x*]$/i.test(value)) return 'All versions';
    if (/^v?\d+\.\d+(?:\.[xX*])?$/.test(value)) return value.replace(/^v/, '').replace(/\.[xX*]$/, '') + '.x';
    if (/^v?\d+(?:\.[xX*])?(?:\.[xX*])?$/.test(value)) return value.replace(/^v/, '').split('.')[0] + '.x';
    if (/^\d{4}\.\d{2}\.\d{2}[-+].+/.test(value)) return value;
    const exact = parse(value); if (exact) return text(exact);
    if (/^v?\d+\.\d+\.\d+[-+].+/.test(value)) return value.replace(/^v/, '');
    if (/^[~^]/.test(value)) {
        const token = value.slice(1).trim().replace(/^v/, ''), pieces = token.split('.');
        const lo = parse(token + (pieces.length === 1 ? '.0.0' : pieces.length === 2 ? '.0' : ''));
        if (lo) {
            const hi: V = value[0] === '~' ? (pieces.length === 1 ? [lo[0] + 1, 0, 0] : [lo[0], lo[1] + 1, 0])
                : lo[0] > 0 || pieces.length === 1 ? [lo[0] + 1, 0, 0] : lo[1] > 0 || pieces.length < 3 ? [0, lo[1] + 1, 0] : [0, 0, lo[2] + 1];
            return range(lo, hi, false);
        }
    }
    const hyphen = value.split(/\s+[-–]\s+/);
    if (hyphen.length === 2) { const lo = parse(hyphen[0]), hi = parse(hyphen[1]); if (lo && hi && cmp(lo, hi) <= 0) return range(lo, hi, true); }
    const regex = /(>=|<=|>|<|=)?\s*(v?\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?)/g;
    const terms: string[][] = []; let end = 0;
    for (const m of value.matchAll(regex)) {
        if (!/^[ ,&]*$/.test(value.slice(end, m.index))) return value;
        terms.push([m[1] || '=', m[2].replace(/^v/, '')]); end = m.index! + m[0].length;
    }
    if (end !== value.length || !terms.length) return value;
    if (terms.length === 1) return words(terms[0]);
    const fallback = () => terms.map(words).join(' & ');
    if (terms.length === 2) {
        const lower = terms.find(t => t[0] === '>='), upper = terms.find(t => ['<', '<='].includes(t[0]));
        if (lower && upper && (!parse(lower[1]) || !parse(upper[1]))) return `${lower[1]} – ${upper[0] === '<' ? 'before ' : ''}${upper[1]}`;
    }
    let lo: V | null = null, hi: V | null = null, inclusive = false;
    for (const [op, token] of terms) {
        const v = parse(token); if (!v || op === '=' || op === '>') return fallback();
        if (op === '>=') { if (!lo || cmp(lo, v) < 0) lo = v; }
        else if (!hi || cmp(hi, v) > 0 || (cmp(hi, v) === 0 && op === '<')) { hi = v; inclusive = op === '<='; }
    }
    return lo && hi && cmp(lo, hi) <= 0 ? range(lo, hi, inclusive) : fallback();
}
export function formatCompatibleVersions(versions: string[]): string {
    const exact = [...new Set(versions)].map(parse);
    if (exact.length && exact.every((v): v is V => !!v)) {
        exact.sort(cmp); const groups: string[] = []; let start = exact[0], end = start;
        for (const next of exact.slice(1)) {
            if (next[0] === end[0] && next[1] === end[1] && next[2] === end[2] + 1) end = next;
            else { groups.push(range(start, end, true)); start = end = next; }
        }
        groups.push(range(start, end, true)); return groups.join(' & ');
    }
    return [...new Set(versions.map(v => formatManifestVersion(v, versions)))].join(' & ');
}
