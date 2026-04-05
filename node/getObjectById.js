#!/usr/bin/env node

import { fileURLToPath } from 'node:url';
import { fetchObjectById } from './cliBridge.js';

const __filename = fileURLToPath(import.meta.url);

export async function getObjectById(objectId) {
  return fetchObjectById(objectId, {
    showOwner: false,
    showPreviousTransaction: false,
  });
}

if (process.argv[1] === __filename) {
  (async () => {
    const [objectId] = process.argv.slice(2);

    if (!objectId) {
      console.error('Usage: node getObjectById.js <OBJECT_ID>');
      process.exit(1);
    }

    try {
      const data = await getObjectById(objectId);
      console.log(JSON.stringify(data, null, 2));
    } catch (err) {
      console.error('Error fetching object:', err?.message || err);
      process.exit(1);
    }
  })();
}

