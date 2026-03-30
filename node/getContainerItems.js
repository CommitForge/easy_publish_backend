#!/usr/bin/env node

import { fileURLToPath } from 'url';
import {
  createRpcClients,
  fetchObjectByIdWithRetry,
  flattenStructFields,
  isLikelyNotFoundError,
  normalizeId,
} from './objectUtils.js';

// Node ESM __filename
const __filename = fileURLToPath(import.meta.url);

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

// Fetch a single object by ID
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
  } catch (err) {
    if (isLikelyNotFoundError(err)) {
      return null;
    }
    throw err;
  }
}

// -----------------------------
// Fetch container items
// -----------------------------
export async function getContainerItems(containerId, type, maxItems = Infinity) {
  const containerFields = await getObject(containerId);

  if (!containerFields) {
    console.error(`⚠ Container ${containerId} not found on network=${network}`);
    return [];
  }

  if (type === 'container') {
    return [{ object_id: containerId, fields: containerFields }];
  }

  const results = [];
  let count = 0;

  const addItem = async (currentId) => {
    const itemFields = await getObject(currentId);
    if (!itemFields) return null;

    results.push({ object_id: currentId, fields: itemFields });
    return normalizeId(itemFields.prev_id);
  };

  switch (type) {
    case 'data_item': {
      let currentId = normalizeId(containerFields.last_data_item_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        currentId = await addItem(currentId);
        count++;
      }
      break;
    }
    case 'data_type': {
      let currentId = normalizeId(containerFields.last_data_type_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        currentId = await addItem(currentId);
        count++;
      }
      break;
    }
    case 'owner': {
      if (Array.isArray(containerFields.owners) && containerFields.owners.length > 0) {
        for (const o of containerFields.owners) {
          if (count >= maxItems) break;
          results.push({ object_id: normalizeId(o?.id), fields: flattenStructFields(o) });
          count++;
        }
      }
      break;
    }
    case 'child': {
      let currentId = normalizeId(
        containerFields.last_container_child_link_id ?? containerFields.last_container_child_id
      );
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        currentId = await addItem(currentId);
        count++;
      }
      break;
    }
    case 'update_record': {
      let currentId = normalizeId(containerFields.last_update_record_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        const recordFields = await getObject(currentId);
        if (!recordFields) break;
        results.push({ object_id: currentId, fields: recordFields });
        currentId = normalizeId(recordFields?.prev_id);
        count++;
      }
      break;
    }

    // -----------------------------
    // NEW TYPES
    // -----------------------------
    case 'owner_audit': {
      let currentId = normalizeId(containerFields.last_owner_audit_id);
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        currentId = await addItem(currentId);
        count++;
      }
      break;
    }

    case 'data_item_verification': {
      let currentId = normalizeId(
        containerFields.last_data_item_verification_id ?? containerFields.last_data_item_verefication_id
      );
      const seen = new Set();
      while (currentId && count < maxItems && !seen.has(currentId)) {
        seen.add(currentId);
        currentId = await addItem(currentId);
        count++;
      }
      break;
    }

    default:
      console.error(`⚠ Unsupported type requested: ${type}`);
      return [];
  }

  return results;
}

// -----------------------------
// Return full structured container data
// -----------------------------
export async function getContainerData(containerId, type, maxItems = Infinity) {
  const items = await getContainerItems(containerId, type, maxItems);
  return {
    container_id: containerId,
    type,
    count: items.length,
    items,
    previous_cursor: items[items.length - 1]?.object_id ?? null,
    next_cursor: items[0]?.object_id ?? null,
  };
}

// -----------------------------
// CLI entrypoint
// -----------------------------
if (process.argv[1] === __filename) {
  (async () => {
    const [containerId, type] = process.argv.slice(2);
    if (!containerId) {
      console.error('Usage: node getContainerItems.js <CONTAINER_ID> [type]');
      process.exit(1);
    }

    try {
      const data = await getContainerData(containerId, type || 'container');
      console.log(JSON.stringify(data, null, 2));

      if (data.count === 0) {
        console.error(
          `⚠ No items found for container ${containerId}. Verify container ID/network (network=${network}).`
        );
      }
    } catch (err) {
      console.error('⚠ Fatal error fetching container data:', err.message || err);
      if (Array.isArray(err?.attempts) && err.attempts.length > 0) {
        console.error('⚠ RPC attempts:', JSON.stringify(err.attempts));
      }
      process.exit(1);
    }
  })();
}
