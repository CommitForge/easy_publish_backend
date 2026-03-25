#!/usr/bin/env node

import { IotaClient, getFullnodeUrl } from '@iota/iota-sdk/client';
import { fileURLToPath } from 'url';
import { fetchObjectById } from './objectUtils.js';

const __filename = fileURLToPath(import.meta.url);

const client = new IotaClient({
  url: getFullnodeUrl('testnet')
});

// -----------------------------
// Generic fetch by object ID
// -----------------------------
export async function getObjectById(objectId) {
  return fetchObjectById(client, objectId, {
    showType: true,
    showContent: true,
    showOwner: false,
    showDisplay: false,
    throwIfMissing: true,
  });
}

// -----------------------------
// CLI entrypoint
// -----------------------------
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
      console.error('⚠ Error:', err.message || err);
      process.exit(1);
    }
  })();
}
