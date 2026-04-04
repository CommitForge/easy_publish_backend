# Frontend Integration: `/api/items`

Use endpoint:

```text
GET /izipublish/api/items
```

## Minimal Query

```text
userAddress=<wallet-address>
```

## Common Query Shapes

### 1) Browse containers

```text
include=CONTAINER
userAddress=0x...
page=0
pageSize=20
```

### 2) Single container with types

```text
include=CONTAINER,DATA_TYPE
userAddress=0x...
containerId=0x...
page=0
pageSize=20
```

### 3) Single container with items + data item verifications

```text
include=CONTAINER,DATA_TYPE,DATA_ITEM,DATA_ITEM_VERIFICATION
userAddress=0x...
containerId=0x...
page=0
pageSize=20
```

### 4) Data item verification status filtering

```text
include=CONTAINER,DATA_TYPE,DATA_ITEM,DATA_ITEM_VERIFICATION
userAddress=0x...
containerId=0x...
dataItemVerificationVerified=true
page=0
pageSize=20
```

### 5) Data-item search + sort (server-side, before pagination)

```text
include=CONTAINER,DATA_TYPE,DATA_ITEM,DATA_ITEM_VERIFICATION
userAddress=0x...
containerId=0x...
dataItemQuery=oil%20change
dataItemSearchFields=name,description,externalId,externalIndex
dataItemSortBy=created
dataItemSortDirection=desc
page=0
pageSize=20
```

Optional data-item-only filters:

- `dataItemVerified=true|false`
- `dataItemHasRevisions=true|false`
- `dataItemHasVerifications=true|false`
- `dataItemDataType=<exact data type name>`

Cross-container and received filtering:

- `containerScope=accessible|all`
- `dataItemRecipientScope=mine|others|with_recipients|all`
- `dataItemVerificationRecipientScope=mine|others|with_recipients|all`
- `recipientAddress=<wallet-address>` (used for `mine` / `others` scopes)

## Response Shape (important)

```json
{
  "containers": [
    {
      "container": { "id": "..." },
      "dataTypes": [
        {
          "dataType": { "id": "..." },
          "dataItems": [
            {
              "dataItem": { "id": "..." },
              "dataItemVerifications": [ { "id": "...", "verified": true } ]
            }
          ]
        }
      ]
    }
  ],
  "meta": {
    "paginationLevel": "container | data_type | data_item",
    "page": 0,
    "pageSize": 20,
    "totalPages": 3,
    "hasNext": true,
    "includes": ["CONTAINER", "DATA_TYPE", "DATA_ITEM", "DATA_ITEM_VERIFICATION"],
    "filters": {
      "containerId": null,
      "dataTypeId": null,
      "dataItemId": null,
      "dataItemVerificationId": null,
      "dataItemVerificationVerified": null,
      "dataItemRecipientScope": null,
      "dataItemVerificationRecipientScope": null,
      "recipientAddress": null,
      "dataItemQuery": null,
      "dataItemSearchFields": "name,description,externalId,externalIndex",
      "dataItemVerified": null,
      "dataItemHasRevisions": null,
      "dataItemHasVerifications": null,
      "dataItemDataType": null,
      "dataItemSortBy": "created",
      "dataItemSortDirection": "desc",
      "domain": null,
      "containerScope": "accessible"
    },
    "totalContainers": 50,
    "returnedContainers": 20,
    "totalDataTypes": 0,
    "totalDataItems": 0,
    "totalDataItemVerifications": 0,
    "dataItemVerificationFilteredAfterPagination": false,
    "availableDataTypes": ["Maintenance", "Insurance"]
  }
}
```

## Frontend Rules

- Drive pagination by `meta.paginationLevel`.
- For `container`, page controls are for top-level containers.
- For `data_type`, page controls are for container's data types.
- For `data_item`, page controls are for items in selected container scope.
- Data-item search/filter/sort is backend-side and runs before pagination, so `totalDataItems` reflects filtered results.

## Auxiliary Browse APIs

These are intentionally separate from `/api/items`:

### `GET /api/container-child-links`

Use for browsing indexed container-child links:

- supports `containerScope`, search fields, sort, pagination
- returns flattened rows in `{ object_id, fields }` format

### `GET /api/owners`

Use for browsing indexed owners:

- supports `ownerStatus=active|removed|all`
- supports `containerScope`, search fields, sort, pagination
- returns flattened rows in `{ object_id, fields }` format

## Data Item Revisions (Beta)

In `DATA_ITEM` responses, each item node can now include:

```json
"revision": {
  "enabled": true,
  "replaces": ["0xolderItem..."]
}
```

Revision computation is additive and backward-compatible:

- Existing `dataItem` and `dataItemVerifications` fields are unchanged.
- `replaces` is read from `easy_publish.revisions` in `dataItem.content`.
- Transaction `references` are independent and are not used as implicit revision IDs.
- Reverse-link fields (for example `supersededBy`/`latest`) are intentionally not included because they are off-chain derived.
