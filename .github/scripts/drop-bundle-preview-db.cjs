const { MongoClient } = require('mongodb');
async function main() {
    const { MONGODB_URI, DB_NAME, BUNDLE_PREVIEW_ID: id, BUNDLE_PREVIEW_BOUNDARY: boundary } = process.env;
    const branch = boundary === 'branch-preview' && /^[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?$/.test(id || '') &&
        !['main', 'develop', 'dev', 'mock-template'].includes(id) && DB_NAME === `modtale-${id}`;
    const pr = boundary === 'pr-preview' && /^[1-9][0-9]*$/.test(id || '') && DB_NAME === `modtale-pr-${id}`;
    if (!branch && !pr) throw new Error('Invalid preview database identity');
    const client = new MongoClient(MONGODB_URI, { appName: 'modtale-bundle-preview-cleanup', serverSelectionTimeoutMS: 15000 });
    try { await client.connect(); await client.db(DB_NAME).dropDatabase(); }
    finally { await client.close(); }
}
main().catch(() => { console.error('Preview database cleanup failed; private diagnostics suppressed.'); process.exitCode = 1; });
