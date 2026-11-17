// Keeps the PR-preview Atlas free-tier (M0) cluster from being auto-paused for
// inactivity by opening a real connection and doing a tiny read + write.
const { MongoClient } = require('mongodb');

const DB_NAME = 'modtale-keepalive';

async function main() {
    const { MONGODB_URI, GITHUB_RUN_ID } = process.env;
    if (!MONGODB_URI) {
        throw new Error('MONGODB_URI is not set');
    }
    const client = new MongoClient(MONGODB_URI, {
        appName: 'modtale-preview-atlas-keepalive',
        serverSelectionTimeoutMS: 20000,
    });
    try {
        await client.connect();
        const db = client.db(DB_NAME);
        await db.command({ ping: 1 });
        await db.collection('heartbeat').updateOne(
            { _id: 'pr-preview' },
            { $set: { at: new Date(), runId: GITHUB_RUN_ID || 'manual' }, $inc: { count: 1 } },
            { upsert: true },
        );
        const doc = await db.collection('heartbeat').findOne({ _id: 'pr-preview' });
        console.log(`Atlas keepalive ok (heartbeat #${doc.count} at ${doc.at.toISOString()})`);
    } finally {
        await client.close();
    }
}

main().catch(error => {
    // Never print the error message itself: driver errors can echo the URI host/user.
    console.error(`Atlas keepalive failed: ${error.name}`);
    process.exitCode = 1;
});
