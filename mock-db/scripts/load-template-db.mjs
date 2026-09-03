import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { connectMongo } from './mongo-connection.mjs';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..');
const fixtureDir = process.env.MOCK_DB_COLLECTION_DIR
  ? path.resolve(process.env.MOCK_DB_COLLECTION_DIR)
  : path.join(repoRoot, 'mock-db', 'generated', 'collections');

const targetUri = process.env.MOCK_TEMPLATE_MONGODB_URI;
const targetDbName = process.env.MOCK_TEMPLATE_DATABASE_NAME || 'modtale-mock-template';
const sourceDbName = process.env.MOCK_SOURCE_DATABASE_NAME || 'modtale';
const collections = [
  'users',
  'projects',
  'project_monthly_stats',
  'platform_monthly_stats',
  'admin_logs',
  'reports',
  'notifications',
  'api_keys',
  'banned_emails',
  'status_incidents',
  'status_history',
];

if (!targetUri) {
  console.error('MOCK_TEMPLATE_MONGODB_URI is required.');
  process.exit(1);
}

if (targetDbName === sourceDbName) {
  console.error('MOCK_TEMPLATE_DATABASE_NAME must differ from MOCK_SOURCE_DATABASE_NAME.');
  process.exit(1);
}

function reviveExtendedJson(value) {
  if (Array.isArray(value)) {
    return value.map(reviveExtendedJson);
  }

  if (value && typeof value === 'object') {
    const keys = Object.keys(value);
    if (keys.length === 1 && keys[0] === '$date') {
      return new Date(value.$date);
    }

    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [key, reviveExtendedJson(child)])
    );
  }

  return value;
}

function readCollection(collectionName) {
  const filePath = path.join(fixtureDir, `${collectionName}.json`);
  const docs = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  if (!Array.isArray(docs)) {
    throw new Error(`${filePath} must contain a JSON array.`);
  }
  return docs.map(reviveExtendedJson);
}

async function main() {
  const client = await connectMongo(targetUri, {
    appName: 'modtale-mock-template-load',
    label: 'template'
  });

  try {
    const db = client.db(targetDbName);

    for (const collectionName of collections) {
      const docs = readCollection(collectionName);
      const collection = db.collection(collectionName);
      await collection.deleteMany({});
      if (docs.length > 0) {
        await collection.insertMany(docs, { ordered: false });
      }
      console.log(`Loaded ${docs.length} document(s) into ${targetDbName}.${collectionName}`);
    }
  } finally {
    await client.close();
  }
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exit(1);
});
