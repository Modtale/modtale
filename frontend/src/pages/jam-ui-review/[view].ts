import type { APIRoute } from 'astro';
import detail from '@/modules/jam/review/detail.html?raw';
import builder from '@/modules/jam/review/builder.html?raw';
import organizers from '@/modules/jam/review/organizers.html?raw';
import mobile from '@/modules/jam/review/mobile.html?raw';

// Read-only, synthetic screenshots for this branch review. This route never
// authenticates a viewer, fetches real data or runs UI actions, and is unavailable
// outside the existing disposable branch-preview environment.
const fixtures: Record<string, string> = { detail, builder, organizers, mobile };

export const GET: APIRoute = ({ params }) => {
    if (process.env.LOG_ENVIRONMENT !== 'branch-preview') return new Response('Not found', { status: 404 });
    const fixture = fixtures[params.view || ''];
    if (!fixture) return new Response('Not found', { status: 404 });
    return new Response(fixture, {
        headers: {
            'Content-Type': 'text/html; charset=utf-8',
            'Cache-Control': 'private, no-store',
            'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:; frame-src 'self' about:; script-src 'none'; base-uri 'none'; form-action 'none'"
        }
    });
};
