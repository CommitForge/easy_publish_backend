import { IotaClient, getFullnodeUrl } from '@iota/iota-sdk/client';

export function flattenStructFields(obj) {
  if (!obj || typeof obj !== 'object') return obj;

  const result = {};
  for (const [key, value] of Object.entries(obj)) {
    if (value && typeof value === 'object' && 'fields' in value) {
      result[key] = flattenStructFields(value.fields);
    } else {
      result[key] = value;
    }
  }
  return result;
}

export function normalizeId(id) {
  if (!id) return null;
  if (typeof id === 'string') {
    const trimmed = id.trim();
    if (!trimmed || trimmed.toLowerCase() === 'null') return null;
    return trimmed;
  }
  if (typeof id === 'object') {
    return normalizeId(id.id ?? id.fields?.id ?? null);
  }
  return null;
}

function splitCsv(value) {
  if (!value || typeof value !== 'string') return [];
  return value
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean);
}

export function resolveRpcUrls({ network = 'testnet' } = {}) {
  const envUrls = splitCsv(process.env.IOTA_RPC_URLS);
  if (envUrls.length > 0) {
    return [...new Set(envUrls)];
  }

  const singleUrl = (process.env.IOTA_RPC_URL || '').trim();
  if (singleUrl) {
    return [singleUrl];
  }

  return [getFullnodeUrl(network)];
}

export function createRpcClients({ network = 'testnet' } = {}) {
  return resolveRpcUrls({ network }).map((url) => ({
    url,
    client: new IotaClient({ url }),
  }));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function isLikelyNotFoundError(error) {
  const message = (error?.message || String(error || '')).toLowerCase();
  return (
    message.includes('not found') ||
    message.includes('object does not exist')
  );
}

export function isLikelyRetryableRpcError(error) {
  const message = (error?.message || String(error || '')).toLowerCase();
  return (
    message.includes('fetch failed') ||
    message.includes('timed out') ||
    message.includes('timeout') ||
    message.includes('econnreset') ||
    message.includes('econnrefused') ||
    message.includes('enotfound') ||
    message.includes('eai_again') ||
    message.includes('socket hang up') ||
    message.includes('tls') ||
    message.includes('network')
  );
}

export async function fetchObjectById(
  client,
  objectId,
  {
    showType = true,
    showContent = true,
    showOwner = false,
    showDisplay = false,
    throwIfMissing = true,
  } = {}
) {
  if (!objectId) {
    if (throwIfMissing) throw new Error('objectId is required');
    return null;
  }

  const res = await client.getObject({
    id: objectId,
    options: {
      showType,
      showContent,
      showOwner,
      showDisplay,
    },
  });

  if (!res?.data || (showContent && !res.data.content)) {
    if (throwIfMissing) throw new Error(`Object ${objectId} not found`);
    return null;
  }

  return {
    object_id: objectId,
    object_type: res.data.type ?? null,
    fields: res.data.content?.fields
      ? flattenStructFields(res.data.content.fields)
      : null,
  };
}

export async function fetchObjectByIdWithRetry(
  rpcClients,
  objectId,
  fetchOptions = {},
  {
    attemptsPerUrl = 2,
    retryDelayMs = 400,
  } = {}
) {
  if (!objectId) {
    throw new Error('objectId is required');
  }

  if (!Array.isArray(rpcClients) || rpcClients.length === 0) {
    throw new Error('No RPC clients configured');
  }

  const attempts = [];
  let lastError = null;
  const safeAttempts = Number.isFinite(attemptsPerUrl) && attemptsPerUrl > 0
    ? Math.floor(attemptsPerUrl)
    : 1;
  const safeRetryDelayMs = Number.isFinite(retryDelayMs) && retryDelayMs >= 0
    ? Math.floor(retryDelayMs)
    : 0;

  for (const rpcClient of rpcClients) {
    const url = rpcClient?.url ?? '<unknown>';
    const client = rpcClient?.client;
    if (!client) continue;

    for (let attempt = 1; attempt <= safeAttempts; attempt++) {
      try {
        return await fetchObjectById(client, objectId, fetchOptions);
      } catch (error) {
        lastError = error;
        attempts.push({
          url,
          attempt,
          message: error?.message || String(error),
        });

        if (isLikelyNotFoundError(error)) {
          const notFoundError = new Error(
            `Object ${objectId} not found on RPC URL ${url}`,
            { cause: error }
          );
          notFoundError.attempts = attempts;
          throw notFoundError;
        }

        if (attempt < safeAttempts && safeRetryDelayMs > 0 && isLikelyRetryableRpcError(error)) {
          await sleep(safeRetryDelayMs * attempt);
        }
      }
    }
  }

  const attemptedUrls = rpcClients.map((item) => item?.url).filter(Boolean).join(', ');
  const error = new Error(
    `RPC fetch failed for object ${objectId}. Attempted URLs: [${attemptedUrls}]. Last error: ${lastError?.message || 'unknown error'}`,
    { cause: lastError || undefined }
  );
  error.attempts = attempts;
  throw error;
}

export function createObjectFetcher(client, options = {}) {
  return function fetchObject(objectId) {
    return fetchObjectById(client, objectId, options);
  };
}
