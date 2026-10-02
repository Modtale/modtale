import { api } from '@/utils/api';
export const diagnosticReasons = {
    INVALID_PROJECT_ID: 'Project identifier needs repair.',
    INVALID_VERSION_ID: 'Version identifier needs repair.',
    DUPLICATE_VERSION_ID: 'Multiple versions share this identifier. Resolve the ambiguity before taking action.',
    INVALID_REQUEST: 'The stored review request or attempt is invalid.',
    INVALID_ARTIFACT: 'The stored file path or file hash is invalid.',
    INVALID_MANUAL_MODE: 'The stored rescan setting is invalid.',
    INVALID_SCAN_STATE: 'The stored scan state is not recognized.',
    MISSING_BINDING: 'The remote review reference is missing.',
    INVALID_BINDING: 'The stored review reference cannot be read.',
    BINDING_MISMATCH: 'The stored review reference does not match this version.',
    INVALID_POLL: 'The stored polling state is invalid.',
    POLL_WITHOUT_REMOTE_STATE: 'Polling state is attached to a version outside remote review.',
} as const;
export type DiagnosticReason = keyof typeof diagnosticReasons;
export interface DiagnosticItem { position: { projectIdType: 'STRING' | 'OBJECT_ID'; projectId: string; versionIndex: number }; versionId: string | null; reasons: DiagnosticReason[]; }
export interface DiagnosticPage { items: DiagnosticItem[]; nextCursor: string | null; examinedSlots: number; scope: 'PENDING_SCAN_STRUCTURE'; }
const boundedIndex = (n: unknown): n is number => Number.isInteger(n) && (n as number) >= 0 && (n as number) <= 16777216;
export function validDiagnosticCursor(value: unknown): value is string {
    if (typeof value !== 'string' || value.length > 810) return false;
    const match = /^d1\.([av])\.1\.([so])\.(0|[1-9][0-9]{0,7})\.([A-Za-z0-9_-]{1,684})$/.exec(value);
    if (!match || !boundedIndex(Number(match[3])) || (match[1] === 'a' && match[3] !== '0')) return false;
    try {
        const raw = atob(match[4].replace(/-/g, '+').replace(/_/g, '/'));
        const decoded = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(Uint8Array.from(raw, c => c.charCodeAt(0)));
        const canonical = btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        return canonical === match[4] && (match[2] === 'o' ? /^[0-9a-f]{24}$/.test(decoded) : decoded.length > 0 && [...decoded].length <= 128);
    } catch { return false; }
}
export function validateDiagnosticPage(value: unknown): DiagnosticPage {
    const page = value as DiagnosticPage;
    if (!page || page.scope !== 'PENDING_SCAN_STRUCTURE' || !Array.isArray(page.items) || page.items.length > 25
        || !Number.isInteger(page.examinedSlots) || page.examinedSlots < page.items.length || page.examinedSlots > 25
        || (page.nextCursor !== null && !validDiagnosticCursor(page.nextCursor))) throw new Error('Invalid review diagnostic response.');
    const keys = new Set<string>();
    for (const item of page.items) {
        const p = item?.position;
        if (!p || !['STRING', 'OBJECT_ID'].includes(p.projectIdType) || typeof p.projectId !== 'string' || !p.projectId || [...p.projectId].length > 128
            || (p.projectIdType === 'OBJECT_ID' && !/^[0-9a-f]{24}$/.test(p.projectId)) || !boundedIndex(p.versionIndex)
            || (item.versionId !== null && (typeof item.versionId !== 'string' || !item.versionId.trim() || item.versionId.length > 128 || /[\x00-\x1f\x7f-\x9f]/.test(item.versionId)))
            || !Array.isArray(item.reasons) || item.reasons.length < 1 || item.reasons.length > 12
            || item.reasons.some(reason => typeof reason !== 'string' || !Object.hasOwn(diagnosticReasons, reason)) || new Set(item.reasons).size !== item.reasons.length)
            throw new Error('Invalid review diagnostic entry.');
        const key = JSON.stringify([p.projectIdType, p.projectId, p.versionIndex]);
        if (keys.has(key)) throw new Error('Duplicate review diagnostic position.');
        keys.add(key);
    }
    return page;
}
export async function getDiagnosticPage(cursor: string | null, signal: AbortSignal): Promise<DiagnosticPage> {
    if (cursor !== null && !validDiagnosticCursor(cursor)) throw new Error('Invalid review diagnostic cursor.');
    const response = await api.get('/admin/verification/diagnostics/page', { params: { cursor: cursor ?? undefined, limit: 25 }, signal });
    const page = validateDiagnosticPage(response.data);
    if (cursor !== null && page.nextCursor === cursor) throw new Error('Diagnostics did not advance. Refresh from start.');
    return page;
}
