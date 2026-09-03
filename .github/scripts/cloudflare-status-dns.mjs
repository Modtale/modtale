#!/usr/bin/env node

const API_BASE = "https://api.cloudflare.com/client/v4";
const ZONE_NAME = "modtale.net";
const RECORD_NAME = "status.modtale.net";
const RECORD_TARGET = "ghs.googlehosted.com";

const token = process.env.CLOUDFLARE_API_TOKEN;
if (!token) {
  throw new Error("CLOUDFLARE_API_TOKEN is required.");
}

async function cloudflare(path, init = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });
  const payload = await response.json();
  if (!response.ok || payload.success === false) {
    const message = (payload.errors || [])
      .map((error) => error.message)
      .filter(Boolean)
      .join("; ");
    throw new Error(`Cloudflare API ${init.method || "GET"} ${path} failed: ${message || response.status}`);
  }
  return payload;
}

const zones = await cloudflare(`/zones?name=${encodeURIComponent(ZONE_NAME)}&status=active`);
const zone = zones.result?.find((candidate) => candidate.name === ZONE_NAME);
if (!zone?.id) {
  throw new Error(`Active Cloudflare zone '${ZONE_NAME}' was not found.`);
}

const records = await cloudflare(
  `/zones/${zone.id}/dns_records?type=CNAME&name=${encodeURIComponent(RECORD_NAME)}`,
);
const existing = records.result?.[0];
const desired = {
  type: "CNAME",
  name: RECORD_NAME,
  content: RECORD_TARGET,
  proxied: false,
  ttl: 300,
};

if (
  existing &&
  existing.content?.replace(/\.$/, "") === RECORD_TARGET &&
  existing.proxied === false &&
  existing.ttl === desired.ttl
) {
  console.log(`${RECORD_NAME} already points directly to ${RECORD_TARGET}.`);
} else if (existing) {
  await cloudflare(`/zones/${zone.id}/dns_records/${existing.id}`, {
    method: "PUT",
    body: JSON.stringify(desired),
  });
  console.log(`Updated ${RECORD_NAME} to ${RECORD_TARGET}.`);
} else {
  await cloudflare(`/zones/${zone.id}/dns_records`, {
    method: "POST",
    body: JSON.stringify(desired),
  });
  console.log(`Created ${RECORD_NAME} pointing to ${RECORD_TARGET}.`);
}
