import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..');
const fixtureDir = process.env.MOCK_DB_COLLECTION_DIR
  ? path.resolve(process.env.MOCK_DB_COLLECTION_DIR)
  : path.join(repoRoot, 'mock-db', 'generated', 'collections');
const outputPath = process.env.MOCK_R2_OBJECT_KEYS_FILE
  ? path.resolve(process.env.MOCK_R2_OBJECT_KEYS_FILE)
  : path.join(repoRoot, 'mock-db', 'generated', 'r2-object-keys.txt');

const versionArtifactFields = new Set(['fileUrl', 'cachedFileUrl', 'artifactUrl']);
const dependencyArtifactFields = new Set(['cachedFileUrl', 'externalFileUrl', 'fileUrl']);

function trimToNull(value) {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function stripLeadingSlash(value) {
  let stripped = String(value || '').trim();
  while (stripped.startsWith('/')) {
    stripped = stripped.slice(1);
  }
  return stripped;
}

function normalizeObjectKey(value) {
  let key = trimToNull(value);
  if (!key) return null;

  const query = key.indexOf('?');
  if (query >= 0) key = key.slice(0, query);

  const fragment = key.indexOf('#');
  if (fragment >= 0) key = key.slice(0, fragment);

  key = stripLeadingSlash(key);
  return key || null;
}

function parseUrl(value) {
  try {
    return new URL(value);
  } catch {
    return null;
  }
}

function isFirstPartyStorageHost(hostname) {
  const host = String(hostname || '').toLowerCase();
  return host === 'cdn.modtale.net' || host.endsWith('.r2.dev');
}

function r2ObjectKey(rawLocation) {
  const value = trimToNull(rawLocation);
  if (!value) return null;

  if (value.startsWith('/api/files/proxy/')) {
    return normalizeObjectKey(value.slice('/api/files/proxy/'.length));
  }

  const url = parseUrl(value);
  if (url && (url.protocol === 'http:' || url.protocol === 'https:')) {
    let key = stripLeadingSlash(url.pathname);
    if (!key) return null;

    if (key.startsWith('api/files/proxy/')) {
      return normalizeObjectKey(key.slice('api/files/proxy/'.length));
    }

    if (isFirstPartyStorageHost(url.hostname)) {
      return normalizeObjectKey(key);
    }

    return null;
  }

  return normalizeObjectKey(value);
}

function addR2KeysFromFields(document, fields, keys) {
  if (!document || typeof document !== 'object') return;

  for (const field of fields) {
    const key = r2ObjectKey(document[field]);
    if (key) keys.add(key);
  }
}

function collectProjectKeys(project, keys) {
  const versions = Array.isArray(project?.versions) ? project.versions : [];
  for (const version of versions) {
    addR2KeysFromFields(version, versionArtifactFields, keys);

    const dependencies = Array.isArray(version?.dependencies) ? version.dependencies : [];
    for (const dependency of dependencies) {
      addR2KeysFromFields(dependency, dependencyArtifactFields, keys);
    }
  }
}

const projectsPath = path.join(fixtureDir, 'projects.json');
const projects = JSON.parse(fs.readFileSync(projectsPath, 'utf8'));
if (!Array.isArray(projects)) {
  throw new Error(`${projectsPath} must contain a JSON array.`);
}

const keys = new Set();
for (const project of projects) {
  collectProjectKeys(project, keys);
}

const sortedKeys = [...keys].sort();
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, sortedKeys.join('\n') + (sortedKeys.length > 0 ? '\n' : ''));

console.log(`Collected ${sortedKeys.length} first-party R2 artifact key(s) from ${projects.length} sanitized project fixture(s).`);
console.log(`R2 object key list: ${outputPath}`);
