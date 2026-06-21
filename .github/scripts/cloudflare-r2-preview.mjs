#!/usr/bin/env node

import { createHash } from "node:crypto";

const API_BASE = "https://api.cloudflare.com/client/v4";
const R2_BUCKET_ITEM_READ = "Workers R2 Storage Bucket Item Read";
const R2_BUCKET_ITEM_WRITE = "Workers R2 Storage Bucket Item Write";

const mode = process.argv[2];

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable ${name}.`);
  }
  return value;
}

function firstRequired(names) {
  for (const name of names) {
    if (process.env[name]) {
      return process.env[name];
    }
  }
  throw new Error(`Missing required environment variable; set one of ${names.join(", ")}.`);
}

function optional(name, fallback = "") {
  return process.env[name] || fallback;
}

function normalizeJurisdiction(value) {
  const jurisdiction = value || "default";
  if (!["default", "eu", "fedramp"].includes(jurisdiction)) {
    throw new Error(`Unsupported R2 jurisdiction '${jurisdiction}'.`);
  }
  return jurisdiction;
}

function endpointFor(accountId, jurisdiction) {
  if (jurisdiction === "eu") {
    return `https://${accountId}.eu.r2.cloudflarestorage.com`;
  }
  if (jurisdiction === "fedramp") {
    return `https://${accountId}.fedramp.r2.cloudflarestorage.com`;
  }
  return `https://${accountId}.r2.cloudflarestorage.com`;
}

function bucketResource(accountId, jurisdiction, bucketName) {
  return `com.cloudflare.edge.r2.bucket.${accountId}_${jurisdiction}_${bucketName}`;
}

function mask(value) {
  if (process.env.GITHUB_ACTIONS === "true" && value) {
    console.log(`::add-mask::${value}`);
  }
}

function appendGitHubEnv(values) {
  const githubEnv = process.env.GITHUB_ENV;
  if (!githubEnv) {
    return;
  }

  const lines = Object.entries(values).map(([key, value]) => `${key}=${value}`);
  return import("node:fs").then(({ appendFileSync }) => {
    appendFileSync(githubEnv, `${lines.join("\n")}\n`, "utf8");
  });
}

async function cloudflare(token, path, init = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { success: false, errors: [{ message: text }] };
    }
  }

  if (!response.ok || payload?.success === false) {
    const messages = [
      ...(payload?.errors || []),
      ...(payload?.messages || []),
    ]
      .map((item) => item?.message || String(item))
      .filter(Boolean)
      .join("; ");

    const error = new Error(
      `Cloudflare API ${init.method || "GET"} ${path} failed with ${response.status}: ${messages || response.statusText}`,
    );
    error.status = response.status;
    error.payload = payload;
    throw error;
  }

  return payload;
}

async function bucketExists(accountId, token, bucketName, jurisdiction) {
  try {
    await cloudflare(
      token,
      `/accounts/${accountId}/r2/buckets/${encodeURIComponent(bucketName)}`,
      { headers: { "cf-r2-jurisdiction": jurisdiction } },
    );
    return true;
  } catch (error) {
    if (error.status === 404) {
      return false;
    }
    throw error;
  }
}

async function ensureBucket(accountId, token, bucketName, jurisdiction) {
  if (await bucketExists(accountId, token, bucketName, jurisdiction)) {
    console.log(`R2 bucket '${bucketName}' already exists.`);
    return;
  }

  const body = {
    name: bucketName,
  };
  const locationHint = optional("CLOUDFLARE_R2_LOCATION_HINT");
  const storageClass = optional("CLOUDFLARE_R2_STORAGE_CLASS");
  if (locationHint) {
    body.locationHint = locationHint;
  }
  if (storageClass) {
    body.storageClass = storageClass;
  }

  await cloudflare(token, `/accounts/${accountId}/r2/buckets`, {
    method: "POST",
    headers: { "cf-r2-jurisdiction": jurisdiction },
    body: JSON.stringify(body),
  });
  console.log(`Created R2 bucket '${bucketName}'.`);
}

async function permissionGroupId(token, permissionName) {
  const payload = await cloudflare(token, "/user/tokens/permission_groups");
  const match = payload.result?.find((group) => group.name === permissionName);
  if (!match?.id) {
    throw new Error(`Cloudflare permission group '${permissionName}' was not found.`);
  }
  return match.id;
}

async function revokeToken(token, tokenId, reason) {
  if (!tokenId) {
    return;
  }
  try {
    await cloudflare(token, `/user/tokens/${encodeURIComponent(tokenId)}`, {
      method: "DELETE",
    });
    console.log(`Revoked Cloudflare token '${tokenId}'${reason ? ` (${reason})` : ""}.`);
  } catch (error) {
    if (error.status === 404) {
      console.log(`Cloudflare token '${tokenId}' was already absent.`);
      return;
    }
    throw error;
  }
}

async function createRuntimeToken(accountId, token, bucketName, jurisdiction) {
  const readGroupId = await permissionGroupId(token, R2_BUCKET_ITEM_READ);
  const writeGroupId = await permissionGroupId(token, R2_BUCKET_ITEM_WRITE);
  const tokenName = optional("R2_TOKEN_NAME", `modtale preview ${bucketName}`);
  const expiresOn = optional("R2_TOKEN_EXPIRES_ON");

  const body = {
    name: tokenName,
    policies: [
      {
        effect: "allow",
        resources: {
          [bucketResource(accountId, jurisdiction, bucketName)]: "*",
        },
        permission_groups: [{ id: readGroupId }, { id: writeGroupId }],
      },
    ],
  };

  if (expiresOn) {
    body.expires_on = expiresOn;
  }

  const payload = await cloudflare(token, "/user/tokens", {
    method: "POST",
    body: JSON.stringify(body),
  });

  const accessKey = payload.result?.id;
  const tokenValue = payload.result?.value;
  if (!accessKey || !tokenValue) {
    throw new Error("Cloudflare did not return the expected token id/value pair.");
  }

  const secretKey = createHash("sha256").update(tokenValue).digest("hex");
  mask(accessKey);
  mask(secretKey);
  mask(tokenValue);

  return {
    accessKey,
    secretKey,
    tokenId: accessKey,
    endpoint: endpointFor(accountId, jurisdiction),
  };
}

async function provision() {
  const accountId = required("CLOUDFLARE_ACCOUNT_ID");
  const bucketToken = firstRequired([
    "CLOUDFLARE_R2_PROVISIONER",
    "CLOUDFLARE_R2_BUCKET_PROVISIONER_TOKEN",
  ]);
  const tokenProvisioner = required("CLOUDFLARE_API_TOKEN_PROVISIONER");
  const bucketName = required("R2_BUCKET_NAME");
  const jurisdiction = normalizeJurisdiction(optional("CLOUDFLARE_R2_JURISDICTION"));

  await ensureBucket(accountId, bucketToken, bucketName, jurisdiction);

  const credentials = await createRuntimeToken(
    accountId,
    tokenProvisioner,
    bucketName,
    jurisdiction,
  );

  await appendGitHubEnv({
    R2_RUNTIME_ACCESS_KEY: credentials.accessKey,
    R2_RUNTIME_SECRET_KEY: credentials.secretKey,
    R2_RUNTIME_TOKEN_ID: credentials.tokenId,
    R2_RUNTIME_ENDPOINT: credentials.endpoint,
  });

  console.log(`Provisioned bucket-scoped R2 runtime token for '${bucketName}'.`);
}

async function ensureOnly() {
  const accountId = required("CLOUDFLARE_ACCOUNT_ID");
  const bucketToken = firstRequired([
    "CLOUDFLARE_R2_PROVISIONER",
    "CLOUDFLARE_R2_BUCKET_PROVISIONER_TOKEN",
  ]);
  const bucketName = required("R2_BUCKET_NAME");
  const jurisdiction = normalizeJurisdiction(optional("CLOUDFLARE_R2_JURISDICTION"));

  await ensureBucket(accountId, bucketToken, bucketName, jurisdiction);
  await appendGitHubEnv({
    R2_RUNTIME_ENDPOINT: endpointFor(accountId, jurisdiction),
  });
}

async function cleanup() {
  const accountId = required("CLOUDFLARE_ACCOUNT_ID");
  const bucketToken = firstRequired([
    "CLOUDFLARE_R2_PROVISIONER",
    "CLOUDFLARE_R2_BUCKET_PROVISIONER_TOKEN",
  ]);
  const bucketName = required("R2_BUCKET_NAME");
  const jurisdiction = normalizeJurisdiction(optional("CLOUDFLARE_R2_JURISDICTION"));
  const tokenProvisioner = optional("CLOUDFLARE_API_TOKEN_PROVISIONER");

  try {
    await cloudflare(
      bucketToken,
      `/accounts/${accountId}/r2/buckets/${encodeURIComponent(bucketName)}`,
      {
        method: "DELETE",
        headers: { "cf-r2-jurisdiction": jurisdiction },
      },
    );
    console.log(`Deleted R2 bucket '${bucketName}'.`);
  } catch (error) {
    if (error.status === 404) {
      console.log(`R2 bucket '${bucketName}' was already absent.`);
    } else {
      throw error;
    }
  }

  if (tokenProvisioner) {
    await revokeToken(tokenProvisioner, optional("R2_RUNTIME_TOKEN_ID"), "preview cleanup");
  }
}

try {
  if (mode === "provision") {
    await provision();
  } else if (mode === "ensure-bucket") {
    await ensureOnly();
  } else if (mode === "cleanup") {
    await cleanup();
  } else {
    throw new Error(
      "Usage: cloudflare-r2-preview.mjs <provision|ensure-bucket|cleanup>",
    );
  }
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
