import { IotaClient, getFullnodeUrl } from '@iota/iota-sdk/client';
import { Wallet, WalletAccount } from '@iota/dapp-kit';

// IOTA client for reading data
const client = new IotaClient({
    url: getFullnodeUrl('testnet'),
});

/**
 * Normalize Move object IDs to string
 */
function normalizeId(id: any): string | null {
    if (!id) return null;
    if (typeof id === 'string') return id;
    if (id.bytes) return id.bytes;
    if (id.id) return id.id;
    return null;
}

/**
 * Fetch container items from your backend API
 */
export async function fetchContainerItems(containerId: string, type: string) {
    const res = await fetch(`/api/items?containerId=${containerId}&type=${type}`);
    if (!res.ok) throw new Error('Failed to fetch items');
    return await res.json();
}

/**
 * Connect to Wallet Standard wallet (compatible with @iota/dapp-kit)
 */
export async function connectWallet(): Promise<WalletAccount | null> {
    try {
        const wallet = new Wallet(); // DApp Kit wallet
        const [account] = await wallet.requestAccounts();
        if (!account) throw new Error("No account found");
        console.log("Connected wallet:", account.address);
        return account;
    } catch (err) {
        console.error("Wallet connect failed:", err);
        return null;
    }
}

/**
 * Render fetched items into a collapsible table
 */
export function renderItems(items: any[], rootId: string) {
    const root = document.getElementById(rootId);
    if (!root) return;

    let html = `<table>
        <thead>
            <tr>
                <th>Object ID</th>
                <th>Name</th>
                <th>Description</th>
                <th>Content</th>
                <th>External ID</th>
                <th>Creator</th>
                <th>Data Type ID</th>
                <th>Tag Index</th>
                <th>Previous</th>
            </tr>
        </thead>
        <tbody>`;

    items.forEach(item => {
        html += `<tr>
            <td><div class="collapsible">${item.object_id}</div></td>
            <td><div class="collapsible">${item.fields.name}</div></td>
            <td><div class="collapsible">${item.fields.description}</div></td>
            <td><div class="collapsible">${item.fields.content}</div></td>
            <td><div class="collapsible">${item.fields.external_id}</div></td>
            <td><div class="collapsible">${item.fields.creator}</div></td>
            <td><div class="collapsible">${item.fields.data_type_id}</div></td>
            <td><div class="collapsible">${item.fields.tag_index}</div></td>
            <td><div class="collapsible">${item.fields.prev || '-'}</div></td>
        </tr>`;
    });

    html += `</tbody></table>`;
    root.innerHTML = html;

    // Make cells collapsible
    root.querySelectorAll('.collapsible').forEach(cell => {
        cell.addEventListener('click', () => cell.classList.toggle('expanded'));
    });
}
