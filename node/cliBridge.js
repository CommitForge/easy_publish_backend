#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const DEFAULT_CLI_DIR = path.resolve(process.cwd(), '..', 'easy_publish_cli', 'easy_publish_cli');
const DEFAULT_ATTEMPTS_PER_URL = 2;
const DEFAULT_RETRY_DELAY_MS = 400;

let cachedIotaModule = null;

function parsePositiveInt(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function parseNonNegativeInt(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function sleep(ms) {
  if (!ms || ms <= 0) return Promise.resolve();
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function resolveCliDir() {
  const configured = String(process.env.EASY_PUBLISH_CLI_DIR || DEFAULT_CLI_DIR).trim();
  return configured || DEFAULT_CLI_DIR;
}

export function resolveNetwork() {
  const network = String(process.env.IOTA_NETWORK || process.env.IZIPUB_NETWORK || 'mainnet').trim();
  return network || 'mainnet';
}

export function normalizeId(value) {
  if (value === null || value === undefined) return null;

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    const normalized = String(value).trim();
    return !normalized || normalized.toLowerCase() === 'null' ? null : normalized;
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      const id = normalizeId(item);
      if (id) return id;
    }
    return null;
  }

  if (typeof value === 'object') {
    return (
      normalizeId(value.id)
      || normalizeId(value.object_id)
      || normalizeId(value.bytes)
      || normalizeId(value.some)
      || normalizeId(value.value)
      || normalizeId(value.vec)
      || normalizeId(value.fields?.id)
      || null
    );
  }

  return null;
}

export function isLikelyNotFoundError(error) {
  const message = String(error?.message || error || '').toLowerCase();
  return (
    message.includes('not found')
    || message.includes('does not exist')
    || message.includes('objectnotexist')
    || message.includes('could not find')
  );
}

export function isLikelyRetryableRpcError(error) {
  const message = String(error?.message || error || '').toLowerCase();
  return (
    message.includes('fetch failed')
    || message.includes('timed out')
    || message.includes('timeout')
    || message.includes('econnreset')
    || message.includes('econnrefused')
    || message.includes('enotfound')
    || message.includes('eai_again')
    || message.includes('network')
    || message.includes('socket hang up')
    || message.includes('tls')
  );
}

async function loadIotaModule() {
  if (cachedIotaModule) {
    return cachedIotaModule;
  }

  const cliDir = resolveCliDir();
  const iotaPath = path.join(cliDir, 'lib', 'iota.js');

  if (!fs.existsSync(iotaPath)) {
    throw new Error(
      `easy_publish_cli iota module not found at ${iotaPath}. Set EASY_PUBLISH_CLI_DIR correctly.`
    );
  }

  cachedIotaModule = await import(pathToFileURL(iotaPath).href);
  return cachedIotaModule;
}

export async function createCliClient() {
  const iota = await loadIotaModule();
  if (typeof iota.createClient !== 'function') {
    throw new Error('Invalid easy_publish_cli iota module: createClient missing');
  }
  return iota.createClient(resolveNetwork());
}

export async function fetchObjectById(objectId, options = {}) {
  const normalizedObjectId = normalizeId(objectId);
  if (!normalizedObjectId) {
    throw new Error('objectId is required');
  }

  const attemptsPerUrl = parsePositiveInt(
    process.env.IOTA_RPC_ATTEMPTS_PER_URL || process.env.IOTA_RPC_MAX_ATTEMPTS_PER_URL,
    DEFAULT_ATTEMPTS_PER_URL
  );
  const retryDelayMs = parseNonNegativeInt(
    process.env.IOTA_RPC_RETRY_DELAY_MS,
    DEFAULT_RETRY_DELAY_MS
  );

  const iota = await loadIotaModule();
  const client = await createCliClient();

  let lastError = null;
  for (let attempt = 1; attempt <= attemptsPerUrl; attempt += 1) {
    try {
      return await iota.fetchObject(client, normalizedObjectId, options);
    } catch (error) {
      lastError = error;
      if (attempt >= attemptsPerUrl || !isLikelyRetryableRpcError(error)) {
        throw error;
      }
      await sleep(retryDelayMs * attempt);
    }
  }

  throw lastError || new Error(`Failed to fetch object ${normalizedObjectId}`);
}

export async function findObjectsByOwner(ownerAddress) {
  const normalizedOwner = normalizeId(ownerAddress);
  if (!normalizedOwner) return [];

  const client = await createCliClient();
  const response = await client.findObjects({ owner: normalizedOwner });
  if (Array.isArray(response)) return response;
  if (Array.isArray(response?.data)) return response.data;
  return [];
}
