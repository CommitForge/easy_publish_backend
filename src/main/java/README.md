# `/api/items` Service Notes

This document describes the intended behavior of `NodeService#getContainerTree(...)` and the API contract consumed by frontend clients.

## Query Scope

`/api/items` supports these filter layers:

- Container scope: `containerId`, `domain`, `userAddress`
- Data type scope: `dataTypeId`
- Data item scope: `dataItemId`
- Data item verification scope: `dataItemVerificationId`, `dataItemVerificationVerified`

## Include Values

`include` accepts CSV values from:

- `CONTAINER`
- `DATA_TYPE`
- `DATA_ITEM`
- `DATA_ITEM_VERIFICATION`

Controller defaults:

- No `containerId` and no `include`: `CONTAINER`
- With `containerId` and no `include`: `CONTAINER,DATA_TYPE,DATA_ITEM,DATA_ITEM_VERIFICATION`

## Pagination Model

Pagination level depends on selected scope:

- `container` level when browsing containers (no `containerId`)
- `data_type` level when in single-container mode with only type-level includes
- `data_item` level when in single-container mode with item-level includes

Response includes `meta.paginationLevel` and `meta.totalPages` to drive frontend paging controls.

## Response Meta Contract

`meta` includes:

- `paginationLevel`
- `page`, `pageSize`, `totalPages`, `hasNext`
- `includes`
- `filters`
- `totalContainers`, `returnedContainers`
- `totalDataTypes`, `totalDataItems`, `totalDataItemVerifications`
- `dataItemVerificationFilteredAfterPagination`

## Frontend Notes

- Always branch pagination UX by `meta.paginationLevel`.
- Render nested shape in order: `containers -> dataTypes -> dataItems -> dataItemVerifications`.
- If data item verification filters are active and `dataItemVerificationFilteredAfterPagination=true`, expect that some pages can return fewer data items than requested page size.

## Data Item Revision Hints (Beta)

When `include` contains `DATA_ITEM`, each `dataItems[]` node now includes an additional `revision` object:

```json
{
  "dataItem": {
    "id": "0x...",
    "references": ["0x..."]
  },
  "dataItemVerifications": [],
  "revision": {
    "enabled": true,
    "replaces": ["0x..."]
  }
}
```

Derivation rules:

- Reads `easy_publish.revisions` from `dataItem.content`.
- Accepts flexible key names (for example `replaces`, `previousIds`, `revisionOf`).
- If revisions are enabled but explicit IDs are omitted, falls back to `dataItem.references`.
- This helper is additive and does not replace existing `dataItem` fields.
- Reverse-link fields (for example `supersededBy`/`latest`) are intentionally not included because they are off-chain derived.
