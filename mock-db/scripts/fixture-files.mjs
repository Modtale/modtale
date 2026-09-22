import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const generatedDirectory = fileURLToPath(new URL('../generated/', import.meta.url));
export const defaultFixtureDirectory = path.join(generatedDirectory, 'collections');
export const collectionNames = Object.freeze([
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
]);

export function readFixtureCollections(directory) {
  return new Map(collectionNames.map((name) => {
    const filePath = path.join(directory, `${name}.json`);
    const documents = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    if (!Array.isArray(documents)) {
      throw new Error(`${filePath} must contain a JSON array.`);
    }
    return [name, documents];
  }));
}
