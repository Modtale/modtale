import { parseTree, type Node, type ParseError } from 'jsonc-parser';
import { isMap, isScalar, isSeq, parseDocument } from 'yaml';
import * as TOML from 'smol-toml';

export type Setting = { id: string; label: string; context: string; category: string; type: 'boolean' | 'number' | 'string'; value: string; integer?: boolean };
export type SettingsDocument = { settings: Setting[]; save: (values: Record<string, string>) => string };
const numberPattern = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/;
export function settingLabel(key: string): string {
    const words = key.replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2').replace(/([a-z\d])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').replace(/\bMsgs\b/gi, 'messages').split(/\s+/);
    return words.map((word, i) => /^[A-Z\d]+$/.test(word) ? word : i === 0 ? word[0]?.toUpperCase() + word.slice(1) : word.toLowerCase()).join(' ');
}
function category(key: string, path: (string | number)[]) {
    if (path.length > 1) return settingLabel(String(path[0]));
    if (/death|xploss|leveldown|levelslost/i.test(key)) return 'Death & penalties';
    if (/sound|notification|chat|hud|titles/i.test(key)) return 'Display & sounds';
    if (/stat|health|stamina|mana/i.test(key)) return 'Attributes';
    if (/xp|level|reward/i.test(key)) return 'Progression';
    return 'General';
}
export function settingError(setting: Setting, value: string): string | null {
    if (setting.type === 'number' && !numberPattern.test(value.trim())) return 'Enter a valid number.';
    if (setting.integer && !/^-?\d+$/.test(value.trim())) return 'Enter a whole number.';
    if (setting.type === 'boolean' && value !== 'true' && value !== 'false') return 'Choose on or off.';
    return null;
}
export function readSettings(name: string, text: string): SettingsDocument {
    const settings: Setting[] = [];
    const writes = new Map<string, (value: string) => void>();
    const replacements: { start: number; end: number; text: string }[] = [];
    const add = (path: (string | number)[], type: Setting['type'], value: string, write: (value: string) => void) => {
        if (settings.length >= 1500 || path.length > 16) throw new Error('This file is too complex for the settings editor. Its attached contents are unchanged.');
        const key = String(path.at(-1));
        const id = JSON.stringify(path);
        settings.push({ id, label: settingLabel(type === 'boolean' ? key.replace(/^Enable(?=[A-Z])/, '').replace(/Config$/, '') : key), context: path.slice(0, -1).map(String).map(settingLabel).join(' / '), category: category(key, path), type, value });
        writes.set(id, write);
    };
    let serialize: () => string;
    if (/\.json$/i.test(name)) {
        const errors: ParseError[] = [];
        const root = parseTree(text, errors, { disallowComments: true, allowTrailingComma: false });
        if (errors.length || root?.type !== 'object') throw new Error('This file must contain a valid settings object.');
        const walk = (node: Node, path: (string | number)[]) => {
            if (path.length > 16) throw new Error('This file is too deeply nested to edit.');
            if (node.type === 'object') {
                const keys = new Set<string>();
                node.children?.forEach(property => { const [key, value] = property.children!; if (keys.has(key.value)) throw new Error('Duplicate settings must be resolved before editing.'); keys.add(key.value); walk(value, [...path, key.value]); });
            } else if (node.type === 'array') node.children?.forEach((item, i) => walk(item, [...path, i]));
            else if (node.type === 'string' || node.type === 'boolean' || node.type === 'number') {
                const type = node.type;
                add(path, type, type === 'string' ? node.value : text.slice(node.offset, node.offset + node.length), value => replacements.push({ start: node.offset, end: node.offset + node.length, text: type === 'string' ? JSON.stringify(value) : value.trim() }));
            }
        };
        walk(root, []);
        serialize = () => replacements.sort((a, b) => b.start - a.start).reduce((result, edit) => result.slice(0, edit.start) + edit.text + result.slice(edit.end), text);
    } else if (/\.ya?ml$/i.test(name)) {
        const doc = parseDocument(text, { intAsBigInt: true });
        if (doc.errors.length || !isMap(doc.contents)) throw new Error('This file must contain a valid settings object.');
        const walk = (node: unknown, path: (string | number)[]) => {
            if (path.length > 16) throw new Error('This file is too deeply nested to edit.');
            if (isMap(node)) node.items.forEach(pair => { if (!isScalar(pair.key) || typeof pair.key.value !== 'string') throw new Error('Only named settings can be edited.'); walk(pair.value, [...path, pair.key.value]); });
            else if (isSeq(node)) node.items.forEach((value, i) => walk(value, [...path, i]));
            else if (isScalar(node) && ['string', 'number', 'bigint', 'boolean'].includes(typeof node.value)) {
                const type = typeof node.value === 'bigint' ? 'number' : typeof node.value as Setting['type'];
                // Replace scalar ranges rather than reserializing unrelated numbers, comments or aliases.
                const range = node.range;
                if (!range || type === 'number' && !Number.isFinite(Number(node.value))) return;
                add(path, type, String(node.value), value => replacements.push({ start: range[0], end: range[1], text: type === 'string' ? JSON.stringify(value) : value.trim() }));
            }
        };
        walk(doc.contents, []);
        serialize = () => replacements.sort((a, b) => b.start - a.start).reduce((result, edit) => result.slice(0, edit.start) + edit.text + result.slice(edit.end), text);
    } else if (/\.toml$/i.test(name)) {
        const data = TOML.parse(text, { integersAsBigInt: true });
        const walk = (object: Record<string, unknown> | unknown[], path: (string | number)[]) => {
            if (path.length > 16) throw new Error('This file is too deeply nested to edit.');
            Object.entries(object).forEach(([key, value]) => {
                const next = [...path, Array.isArray(object) ? Number(key) : key];
                if (value && typeof value === 'object' && !(value instanceof Date)) walk(value as Record<string, unknown>, next);
                else if (['boolean', 'string', 'number', 'bigint'].includes(typeof value)) {
                    const type = typeof value === 'bigint' ? 'number' : typeof value as Setting['type'];
                    if (type === 'number' && !Number.isFinite(Number(value))) return;
                    add(next, type, String(value), input => {
                        const parsed = type === 'string' ? input : type === 'boolean' ? input === 'true' : typeof value === 'bigint' ? BigInt(input.trim()) : Number(input);
                        if (typeof parsed === 'number' && !Number.isFinite(parsed)) throw new Error('This number is out of range.');
                        Object.defineProperty(object, key, { value: parsed, enumerable: true, writable: true, configurable: true });
                    });
                    if (typeof value === 'bigint') settings[settings.length - 1].integer = true;
                }
            });
        };
        walk(data, []);
        serialize = () => TOML.stringify(data, { numbersAsFloat: true });
    } else throw new Error('This file is attached. The settings editor supports JSON, YAML and TOML files.');
    if (!settings.length) throw new Error('This file has no editable settings. Its attached contents are unchanged.');
    return { settings, save(values) {
        replacements.length = 0;
        let changed = false;
        for (const setting of settings) {
            const value = values[setting.id] ?? setting.value;
            const error = settingError(setting, value);
            if (error) throw new Error(`${setting.label}: ${error}`);
            if (value !== setting.value) changed = true;
        }
        for (const setting of settings) {
            const value = values[setting.id] ?? setting.value;
            if (value !== setting.value || /\.toml$/i.test(name)) writes.get(setting.id)!(value);
        }
        return changed ? serialize() : text;
    } };
}
