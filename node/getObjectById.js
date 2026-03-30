#!/usr/bin/env node

import { fileURLToPath } from 'url';
import { createRpcClients, fetchObjectByIdWithRetry } from './objectUtils.js';

const __filename = fileURLToPath(import.meta.url);
const network = (process.env.IOTA_NETWORK || 'testnet').trim() || 'testnet';
const attemptsPerUrl = Number.parseInt(
  process.env.IOTA_RPC_ATTEMPTS_PER_URL || process.env.IOTA_RPC_MAX_ATTEMPTS_PER_URL || '2',
  10
);
const retryDelayMs = Number.parseInt(process.env.IOTA_RPC_RETRY_DELAY_MS || '400', 10);
const rpcClients = createRpcClients({ network });

// -----------------------------
// Generic fetch by object ID
// -----------------------------
export async function getObjectById(objectId) {
  return fetchObjectByIdWithRetry(
    rpcClients,
    objectId,
    {
      showType: true,
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
      console.error('⚠ Error:', err?.message || err);
      if (Array.isArray(err?.attempts) && err.attempts.length > 0) {
        console.error('⚠ RPC attempts:', JSON.stringify(err.attempts));
      }
      process.exit(1);
    }
  })();
}
