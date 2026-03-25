// testGetContainerItems.ts
import { getContainerItems } from './getContainerItems.ts'; // make sure this matches export

async function runTest() {
  const containerId = '0x139b35c2c3e23bfdfbbde3576c71c7af50a3e75cb708743237e17dd1ad2901c8'; // replace with your container ID
  const type: 'owner' | 'child' | 'data_type' | 'data_item' = 'owner';

  try {
    const items = await getContainerItems(containerId, type, 10); // fetch max 10
    console.log('Items:', items);
  } catch (err) {
    console.error('Error fetching container items:', err);
  }
}

runTest();
