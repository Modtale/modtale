const { MongoClient } = require('mongodb');

async function main() {
    const { MONGODB_URI, DB_NAME, BRANCH_SLUG } = process.env;
    if (!/^[a-z0-9-]{1,20}$/.test(BRANCH_SLUG || '') ||
            ['main', 'develop', 'dev', 'mock-template'].includes(BRANCH_SLUG) ||
            DB_NAME !== `modtale-${BRANCH_SLUG}`) {
        throw new Error('Refusing to drop a protected or mismatched database');
    }
    const client = new MongoClient(MONGODB_URI, { appName: 'modtale-branch-preview-cleanup', serverSelectionTimeoutMS:15000 });
    try {
        await client.connect();
        await client.db(DB_NAME).dropDatabase();
        console.log(`Removed orphaned preview database ${DB_NAME}`);
    } finally {
        await client.close();
    }
}
main().catch(error => { console.error(`Preview database cleanup failed: ${error.name}`); process.exitCode = 1; });
