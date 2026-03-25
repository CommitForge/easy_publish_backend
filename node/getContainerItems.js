#!/usr/bin/env node

import { IotaClient, getFullnodeUrl } from '@iota/iota-sdk/client';
import { fileURLToPath } from 'url';
import { createObjectFetcher, flattenStructFields, normalizeId } from './objectUtils.js';

// Node ESM __filename
const __filename = fileURLToPath(import.meta.url);

const client = new IotaClient({ url: getFullnodeUrl('testnet') });
const fetchObject = createObjectFetcher(client, {
  showType: false,
  throwIfMissing: false,
});

// Fetch a single object by ID
async function getObject(id) {
  if (!id) return null;
  try {
    const objectData = await fetchObject(id);
    return objectData?.fields ?? null;
  } catch (err) {
    console.error(`⚠ Error fetching object ${id}:`, err.message || err);
    return null;
  }
}

// -----------------------------
// Fetch container items
// -----------------------------
export async function getContainerItems(containerId, type, maxItems = Infinity) {
  const containerFields = await getObject(containerId);

  if (!containerFields) {
    console.error(`⚠ Container ${containerId} not found`);
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
        console.error(`⚠ No items found for container ${containerId}. Verify container ID or network.`);
      }
    } catch (err) {
      console.error('⚠ Fatal error fetching container data:', err.message || err);
      process.exit(1);
    }
  })();
}
