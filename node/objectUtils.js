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

export function createObjectFetcher(client, options = {}) {
  return function fetchObject(objectId) {
    return fetchObjectById(client, objectId, options);
  };
}
