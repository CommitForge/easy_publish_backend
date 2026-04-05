#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import {
  fetchObjectById,
  isLikelyNotFoundError,
  normalizeId,
  resolveNetwork,
} from './cliBridge.js';

const __filename = fileURLToPath(import.meta.url);

async function safeFetchObject(id) {
  const normalizedId = normalizeId(id);
  if (!normalizedId) return null;
  try {
    return await fetchObjectById(normalizedId, {
      showOwner: false,
      showPreviousTransaction: false,
    });
  } catch (error) {
    if (isLikelyNotFoundError(error)) {
      return null;
    }
    throw error;
  }
}

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

async function walkLinkedObjects(startId, maxItems = Infinity) {
  const results = [];
  const seen = new Set();
  let currentId = normalizeId(startId);
  let count = 0;

  while (currentId && count < maxItems && !seen.has(currentId)) {
    seen.add(currentId);
    const object = await safeFetchObject(currentId);
    if (!object) break;

    results.push({
      object_id: currentId,
      fields: object.fields ?? {},
    });

    currentId = normalizeId(object.fields?.prev_id);
    count += 1;
  }

  return results;
}

export async function getContainerItems(containerId, type, maxItems = Infinity) {
  const normalizedContainerId = normalizeId(containerId);
  if (!normalizedContainerId) {
    throw new Error('containerId is required');
  }

  const containerObject = await safeFetchObject(normalizedContainerId);
  if (!containerObject) {
    console.error(`Container ${containerId} not found (network=${resolveNetwork()})`);
    return [];
  }
  const containerFields = containerObject.fields ?? {};

  if (!type || type === 'container') {
    return [{ object_id: normalizedContainerId, fields: containerFields }];
  }

  switch (type) {
    case 'data_item':
      return walkLinkedObjects(containerFields.last_data_item_id, maxItems);
    case 'data_type':
      return walkLinkedObjects(containerFields.last_data_type_id, maxItems);
    case 'child': {
      const head =
        containerFields.last_container_child_link_id
        ?? containerFields.last_container_child_id
        ?? containerFields.last_child_id;
      return walkLinkedObjects(head, maxItems);
    }
    case 'owner_audit':
      return walkLinkedObjects(containerFields.last_owner_audit_id, maxItems);
    case 'update_record':
      return walkLinkedObjects(containerFields.last_update_record_id, maxItems);
    case 'data_item_verification': {
      const head =
        containerFields.last_data_item_verification_id
        ?? containerFields.last_data_item_verefication_id;
      return walkLinkedObjects(head, maxItems);
    }
    case 'owner': {
      const owners = Array.isArray(containerFields.owners) ? containerFields.owners : [];
      return owners.slice(0, maxItems).map((owner) => ({
        object_id: normalizeId(owner?.id),
        fields: normalizeStructFields(owner),
      }));
    }
    default:
      console.error(`Unsupported type requested: ${type}`);
      return [];
  }
}

export async function getContainerData(containerId, type, maxItems = Infinity) {
  const items = await getContainerItems(containerId, type, maxItems);
  return {
    container_id: normalizeId(containerId),
    type,
    count: items.length,
    items,
    previous_cursor: items[items.length - 1]?.object_id ?? null,
    next_cursor: items[0]?.object_id ?? null,
  };
}

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
          `No items found for container ${containerId}. Verify container ID/network (network=${resolveNetwork()}).`
        );
      }
    } catch (err) {
      console.error('Fatal error fetching container data:', err?.message || err);
      process.exit(1);
    }
  })();
}

