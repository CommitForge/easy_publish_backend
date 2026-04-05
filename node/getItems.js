#!/usr/bin/env node

import {
  findObjectsByOwner,
  normalizeId,
  resolveNetwork,
} from './cliBridge.js';
import { getContainerItems } from './getContainerItems.js';

function normalizeStructFields(value) {
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) return value.map(normalizeStructFields);
  if (typeof value !== 'object') return value;

  if ('fields' in value && typeof value.fields === 'object' && value.fields !== null) {
    return normalizeStructFields(value.fields);
  }

  const out = {};
  for (const [key, nested] of Object.entries(value)) {
    out[key] = normalizeStructFields(nested);
  }
  return out;
}

async function getAllContainers(userAddress, maxItems = 100) {
  const objects = await findObjectsByOwner(userAddress);
  return objects.slice(0, maxItems).map((obj) => ({
    object_id: normalizeId(obj?.id),
    fields: normalizeStructFields(obj),
  }));
}

async function resolveItems(containerId, type, userAddress, maxItems = 100) {
  if (type === 'container') {
    return getAllContainers(userAddress, maxItems);
  }

  const normalizedContainerId = normalizeId(containerId);
  if (!normalizedContainerId) {
    throw new Error(`containerId is required for type ${type}`);
  }

  return getContainerItems(normalizedContainerId, type, maxItems);
}

async function main() {
  const containerId = process.argv[2]; // optional for container type
  const type = process.argv[3];
  const userAddress = process.argv[4];

  if (!type || !userAddress) {
    console.error('Usage: node getItems.js [CONTAINER_ID] <type> <userAddress>');
    console.error('Type can be: container, owner, child, data_type, data_item');
    process.exit(1);
  }

  try {
    const items = await resolveItems(containerId, type, userAddress);
    const firstItem = items[items.length - 1];
    const lastItem = items[0];

    console.log(
      JSON.stringify(
        {
          container_id: normalizeId(containerId),
          type,
          count: items.length,
          items,
          previous_cursor: firstItem?.object_id ?? null,
          next_cursor: lastItem?.object_id ?? null,
          network: resolveNetwork(),
        },
        null,
        2
      )
    );
  } catch (err) {
    console.error('Error fetching items:', err?.message || err);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

