#!/usr/bin/env node

import {
  createRpcClients,
  fetchObjectByIdWithRetry,
  isLikelyNotFoundError,
  isLikelyRetryableRpcError,
  normalizeId,
} from './objectUtils.js';

function parsePositiveInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function parseNonNegativeInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

const network = (process.env.IOTA_NETWORK || 'testnet').trim() || 'testnet';
const attemptsPerUrl = parsePositiveInt(
  process.env.IOTA_RPC_ATTEMPTS_PER_URL || process.env.IOTA_RPC_MAX_ATTEMPTS_PER_URL || '2',
  2
);
const retryDelayMs = parseNonNegativeInt(process.env.IOTA_RPC_RETRY_DELAY_MS || '400', 400);
const rpcClients = createRpcClients({ network });

function toObjectArray(response) {
  if (Array.isArray(response)) return response;
  if (Array.isArray(response?.data)) return response.data;
  return [];
}

async function findObjectsByOwner(userAddress) {
  if (!userAddress) return [];

  const attempts = [];
  let lastError = null;

  for (const rpcClient of rpcClients) {
    const url = rpcClient?.url ?? '<unknown>';
    const client = rpcClient?.client;
    if (!client) continue;

    try {
      const response = await client.findObjects({ owner: userAddress });
      return toObjectArray(response);
    } catch (error) {
      lastError = error;
      attempts.push({
        url,
        message: error?.message || String(error),
      });

      if (isLikelyNotFoundError(error)) {
        return [];
      }

      if (!isLikelyRetryableRpcError(error)) {
        break;
      }
    }
  }

  const attemptedUrls = rpcClients.map((item) => item?.url).filter(Boolean).join(', ');
  const error = new Error(
    `RPC findObjects failed for owner ${userAddress}. Attempted URLs: [${attemptedUrls}]. Last error: ${lastError?.message || 'unknown error'}`,
    { cause: lastError || undefined }
  );
  error.attempts = attempts;
  throw error;
}

/**
 * Fetch a Move object by ID
 */
async function getObject(id) {
  const objectId = normalizeId(id);
  if (!objectId) return null;

  try {
    const objectData = await fetchObjectByIdWithRetry(
      rpcClients,
      objectId,
      {
        showType: false,
        showContent: true,
        showOwner: false,
        showDisplay: false,
        throwIfMissing: true,
      },
      {
        attemptsPerUrl,
        retryDelayMs,
      }
    );
    return objectData?.fields ?? null;
  } catch (error) {
    if (isLikelyNotFoundError(error)) {
      return null;
    }
    throw error;
  }
}

/**
 * Fetch all containers owned by a user
 */
async function getAllContainers(userAddress, maxItems = 100) {
  const results = [];
  const searchRes = await findObjectsByOwner(userAddress);
  for (const obj of searchRes.slice(0, maxItems)) {
    results.push({ object_id: normalizeId(obj.id), fields: obj });
  }
  return results;
}

/**
 * Fetch all items of a given type in a container
 */
async function getContainerItems(containerId, type, userAddress, maxItems = 100) {
  if (type === 'container') {
    return getAllContainers(userAddress, maxItems);
  }

  if (!containerId) throw new Error('containerId is required for type ' + type);

  const containerFields = await getObject(containerId);
  if (!containerFields) throw new Error('Container not found: ' + containerId);

  const results = [];
  let count = 0;

  switch (type) {
    case 'data_item': {
      let currentId = normalizeId(containerFields.last_data_item_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        const itemFields = await getObject(currentId);
        if (!itemFields) break;
        results.push({ object_id: currentId, fields: itemFields });
        currentId = normalizeId(itemFields?.prev_id);
        count++;
      }
      break;
    }

    case 'data_type': {
      let currentId = normalizeId(containerFields.last_data_type_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        const itemFields = await getObject(currentId);
        if (!itemFields) break;
        results.push({ object_id: currentId, fields: itemFields });
        currentId = normalizeId(itemFields?.prev_id);
        count++;
      }
      break;
    }

    case 'owner': {
      const owners = Array.isArray(containerFields.owners) ? containerFields.owners : [];
      for (const o of owners) {
        if (count >= maxItems) break;
        results.push({ object_id: normalizeId(o?.id), fields: o });
        count++;
      }
      break;
    }

    case 'child': {
      let currentId = normalizeId(
        containerFields.last_container_child_link_id
          ?? containerFields.last_container_child_id
          ?? containerFields.last_child_id
      );
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        const childFields = await getObject(currentId);
        if (!childFields) break;
        results.push({ object_id: currentId, fields: childFields });
        currentId = normalizeId(childFields?.prev_id);
        count++;
      }
      break;
    }

    default:
      throw new Error(`Unsupported type: ${type}`);
  }

  return results;
}

/**
 * CLI entrypoint
 */
async function main() {
  const containerId = process.argv[2]; // may be undefined
  const type = process.argv[3];
  const userAddress = process.argv[4];

  if (!type || !userAddress) {
    console.error('Usage: node getItems.js [CONTAINER_ID] <type> <userAddress>');
    console.error('Type can be: container, owner, child, data_type, data_item');
    process.exit(1);
  }

  try {
    const items = await getContainerItems(containerId, type, userAddress);

    const firstItem = items[items.length - 1];
    const lastItem = items[0];

    console.log(
      JSON.stringify(
        {
          container_id: containerId ?? null,
          type,
          count: items.length,
          items,
          previous_cursor: firstItem?.object_id ?? null,
          next_cursor: lastItem?.object_id ?? null,
        },
        null,
        2
      )
    );
  } catch (err) {
    console.error('Error fetching items:', err.message || err);
    if (Array.isArray(err?.attempts) && err.attempts.length > 0) {
      console.error('RPC attempts:', JSON.stringify(err.attempts));
    }
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
