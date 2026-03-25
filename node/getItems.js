#!/usr/bin/env node

import { IotaClient, getFullnodeUrl } from '@iota/iota-sdk/client';
import { normalizeId } from './objectUtils.js';

const client = new IotaClient({
  url: getFullnodeUrl('testnet'),
});

/**
 * Fetch a Move object by ID
 */
async function getObject(id) {
  if (!id) return null;
  const res = await client.getObject({ id, options: { showContent: true } });
  if (!res?.data?.content) return null;
  return res.data.content.fields || null;
}

/**
 * Fetch all containers owned by a user
 */
async function getAllContainers(userAddress, maxItems = 100) {
  const results = [];
  // TODO: adapt this to your Chain setup; example using a placeholder
  // For now, we just simulate fetching all containers
  const searchRes = await client.findObjects({ owner: userAddress });
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
    console.error('Error fetching items:', err.message);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
