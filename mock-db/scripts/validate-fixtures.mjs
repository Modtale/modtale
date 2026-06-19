import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..');
const fixtureDir = process.env.MOCK_DB_COLLECTION_DIR
  ? path.resolve(process.env.MOCK_DB_COLLECTION_DIR)
  : path.join(repoRoot, 'mock-db', 'generated', 'collections');

const requiredCollections = [
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

const forbiddenKeyPattern = /(?:token|secret|accessToken|refreshToken|mfaSecret|verificationToken|passwordReset)/i;
const forbiddenStringPatterns = [
  /mongodb(?:\+srv)?:\/\//i,
  /discord\.com\/api\/webhooks/i,
  /BEGIN (?:RSA |EC |OPENSSH |)?PRIVATE KEY/i,
  /AKIA[0-9A-Z]{16}/,
  /(?:password|passwd|pwd)\s*[:=]\s*[^,\s]+/i,
];

const errors = [];

function readCollection(name) {
  const filePath = path.join(fixtureDir, `${name}.json`);
  if (!fs.existsSync(filePath)) {
    errors.push(`Missing fixture file: ${filePath}`);
    return [];
  }

  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    if (!Array.isArray(parsed)) {
      errors.push(`${name}.json must be a JSON array.`);
      return [];
    }
    return parsed;
  } catch (error) {
    errors.push(`${name}.json is not valid JSON: ${error.message}`);
    return [];
  }
}

function walk(value, context) {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => walk(entry, `${context}[${index}]`));
    return;
  }

  if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      const childContext = `${context}.${key}`;

      if (key === 'password' || key === 'keyHash') {
        if (typeof child !== 'string' || !/^\$2[aby]\$\d{2}\$/.test(child)) {
          errors.push(`${childContext} must be a bcrypt hash, not a plaintext secret.`);
        }
      } else if (forbiddenKeyPattern.test(key)) {
        errors.push(`${childContext} uses a forbidden secret-bearing key name.`);
      }

      walk(child, childContext);
    }
    return;
  }

  if (typeof value === 'string') {
    for (const pattern of forbiddenStringPatterns) {
      if (pattern.test(value)) {
        errors.push(`${context} contains a forbidden secret-like string.`);
      }
    }
  }
}

function checkCommentShape(comment, context) {
  const allowedKeys = new Set([
    'id',
    'userId',
    'content',
    'date',
    'updatedAt',
    'upvotes',
    'downvotes',
    'developerReply',
  ]);

  for (const key of Object.keys(comment || {})) {
    if (!allowedKeys.has(key)) {
      errors.push(`${context}.${key} is not part of the public comment fixture shape.`);
    }
  }

  for (const voteField of ['upvotes', 'downvotes']) {
    const votes = comment?.[voteField] || [];
    if (!Array.isArray(votes)) {
      errors.push(`${context}.${voteField} must be an array.`);
      continue;
    }
    for (const voterId of votes) {
      if (!String(voterId).startsWith('mock-')) {
        errors.push(`${context}.${voteField} must use synthetic voter IDs only.`);
      }
    }
  }

  if (comment?.developerReply) {
    checkCommentShape(comment.developerReply, `${context}.developerReply`);
  }
}

const collections = Object.fromEntries(requiredCollections.map((name) => [name, readCollection(name)]));

for (const [name, docs] of Object.entries(collections)) {
  if (docs.length === 0) {
    errors.push(`${name}.json must contain at least one document.`);
  }
  walk(docs, name);
}

const usersById = new Map(collections.users.map((user) => [String(user._id), user]));
for (const user of collections.users) {
  const username = user.username || user._id;
  if (!String(user.email || '').endsWith('@example.test')) {
    errors.push(`User ${username} must use an @example.test email address.`);
  }
  if (user.connectedAccounts?.some((account) => String(account.profileUrl || '').includes('github.com'))) {
    errors.push(`User ${username} must not link to real social profiles.`);
  }
}

const classifications = new Set();
const statuses = new Set();
for (const project of collections.projects) {
  const id = String(project._id || '');
  if (!usersById.has(String(project.authorId))) {
    errors.push(`Project ${id} references missing authorId ${project.authorId}.`);
  }
  if (Array.isArray(project.comments)) {
    if (project.comments.length > 20) {
      errors.push(`Project ${id} has too many comments for a public mock fixture.`);
    }
    project.comments.forEach((comment, index) => checkCommentShape(comment, `projects.${id}.comments[${index}]`));
  }
  classifications.add(project.classification);
  statuses.add(project.status);
}

for (const classification of ['PLUGIN', 'DATA', 'ART', 'SAVE', 'MODPACK']) {
  if (!classifications.has(classification)) {
    errors.push(`Missing project classification coverage: ${classification}`);
  }
}

for (const status of ['PUBLISHED', 'PENDING', 'DRAFT', 'PRIVATE', 'UNLISTED', 'ARCHIVED']) {
  if (!statuses.has(status)) {
    errors.push(`Missing project status coverage: ${status}`);
  }
}

if (errors.length > 0) {
  console.error(`Mock DB fixture validation failed with ${errors.length} issue(s):`);
  for (const error of errors) {
    console.error(` - ${error}`);
  }
  process.exit(1);
}

console.log(`Mock DB fixtures validated: ${fixtureDir}`);
