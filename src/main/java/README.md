# `/api/items` and Auxiliary Browse Service Notes

This document describes the intended behavior of:

- `NodeService#getContainerTree(...)` (`/api/items`)
- auxiliary browse services:
  - `/api/container-child-links`
  - `/api/owners`
- link graph:
  - `/api/link-graph`

and the API contracts consumed by frontend clients.

## Query Scope

`/api/items` supports these filter layers:

- Container scope: `containerId`, `domain`, `userAddress`, `containerScope`
- Data type scope: `dataTypeId`
- Data item scope: `dataItemId`
- Data item verification scope: `dataItemVerificationId`, `dataItemVerificationVerified`
- Recipient scope:
  - `dataItemRecipientScope` (`mine`, `others`, `with_recipients`, `all`)
  - `dataItemVerificationRecipientScope` (`mine`, `others`, `with_recipients`, `all`)
  - `recipientAddress` (used for `mine` / `others`)
- Data item search/sort scope (item-level mode):
  - `dataItemQuery`
  - `dataItemSearchFields` (`name`, `description`, `externalId`, `externalIndex`, `objectId`, `dataType`, `creatorAddr`)
  - `dataItemVerified`
  - `dataItemHasRevisions`
  - `dataItemHasVerifications`
  - `dataItemDataType` (data type name)
  - `dataItemSortBy` (`created`, `name`, `external_index`, `external_id`)
  - `dataItemSortDirection` (`asc` or `desc`)

Default item sort is `created` + `desc` (latest on-chain sequence index first).

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

- `container` level when browsing containers (no `containerId` and non-item include)
- `data_item` level in cross-container item mode (no `containerId` with `DATA_ITEM`)
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
- `availableDataTypes` (item mode convenience list for frontend filter UIs)

## Frontend Notes

- Always branch pagination UX by `meta.paginationLevel`.
- Render nested shape in order: `containers -> dataTypes -> dataItems -> dataItemVerifications`.
- Data item search/filter/sort is applied server-side before pagination.
- Container scope behavior in cross-container item mode:
  - `containerScope=accessible` (default): owned + followed containers
  - `containerScope=all`: all containers
- `dataItemVerificationFilteredAfterPagination` is retained for compatibility but should generally remain `false` now that verification filters are applied pre-pagination.

## Auxiliary Browse Endpoints

These endpoints are intentionally separate from `/api/items` so item-tree logic
remains focused on primary data.

### `/api/container-child-links`

Purpose:

- browse indexed container-child links in table form

Query scope:

- `userAddress`
- optional `containerId`
- `containerScope=accessible|all`
- optional `query`
- optional `searchFields`
- optional `sortBy` (`created|name|external_index|external_id`)
- optional `sortDirection` (`asc|desc`, default `desc`)
- optional `domain`
- `page`, `pageSize`

Response shape:

- top-level pagination keys (`page`, `pageSize`, `totalElements`, `totalPages`, `hasNext`)
- `content[]` entries in `{ object_id, fields }` shape
- `filters` echo map for effective request context

### `/api/owners`

Purpose:

- browse indexed owner rows linked to containers

Query scope:

- `userAddress`
- optional `containerId`
- `containerScope=accessible|all`
- optional `ownerStatus` (`active|removed|all`, default `active`)
- optional `query`
- optional `searchFields`
- optional `sortBy` (`created|address|role|container_name`)
- optional `sortDirection` (`asc|desc`, default `desc`)
- optional `domain`
- `page`, `pageSize`

Response shape:

- top-level pagination keys (`page`, `pageSize`, `totalElements`, `totalPages`, `hasNext`)
- `content[]` entries in `{ object_id, fields }` shape
- `filters` echo map for effective request context

## Link Graph Endpoint

`POST /api/link-graph` powers recipients/references graph dialogs.

Important defaults and limits:

- default `mode`: `recipients`
- default `sourceType`: `data_item`
- default `maxDepth`: `3` (clamped to `1..8`)
- default `maxNodes`: `160` (clamped to `1..500`)
- default `preventCycles`: `true`

If limits are hit, backend returns partial graph and a message in `info`.

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
- Transaction `references` are independent and are not used as implicit revision IDs.
- This helper is additive and does not replace existing `dataItem` fields.
- Reverse-link fields (for example `supersededBy`/`latest`) are intentionally not included because they are off-chain derived.
