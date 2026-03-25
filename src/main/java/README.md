getContainerTree Method Documentation
Purpose

Fetch a container tree including containers, data types, and data items.
Supports two modes:

Browsing containers (no specific container selected)

Viewing a single container (with optional filtering by data type)

The method now supports container-only lazy loading, where all data items are returned for all data types to allow frontend pagination across multiple pages.

Method Signature
public ContainerTreeDto getContainerTree(
String containerId,
String dataTypeId,
String creatorAddr,
int page,
int pageSize,
EnumSet<ContainerTreeIncludeEnum> includes
)

Parameters
Parameter	Type	Description
containerId	String	Optional. If null/blank → browsing mode (list containers). If provided → single container view.
dataTypeId	String	Optional. If provided → fetch items only for this specific data type. Otherwise → fetch items for all data types in the container.
creatorAddr	String	Creator address of the container(s). Used for container filtering in browsing mode.
page	int	Page index for pagination. Applies differently depending on browsing mode vs single container.
pageSize	int	Number of items per page.
includes	EnumSet<ContainerTreeIncludeEnum>	Controls which nodes to include: DATA_TYPE and/or DATA_ITEM.
Return Type
ContainerTreeDto


Contains a list of ContainerNodeDto, each containing:

Container information

List of DataTypeNodeDto, each containing:

Data type information

List of DataItem objects

Behavior
1. Browsing Containers (no containerId)

Paginate by container (page, pageSize).

Each container node includes no data types or items.

Example: page=0, pageSize=21 → first 21 containers.

2. Single Container View (containerId provided)

If dataTypeId is provided:

Fetch only that specific data type.

Fetch all items of that data type.

If dataTypeId is null:

Fetch all data types for the container.

Fetch all items for all data types, no slicing on backend.

Frontend handles pagination across multiple data types automatically.

Group items by data type for proper display.

3. Includes

Only fetch DATA_TYPE nodes if includes contains DATA_TYPE.

Only fetch DATA_ITEM nodes if includes contains DATA_ITEM.

Pagination Rules
Mode	Backend Pagination	Frontend Pagination
Browsing containers	Applied at container level (page, pageSize)	N/A
Single container, specific data type	All items for that data type returned	Frontend slices per page
Single container, all data types	All items from all data types returned	Frontend slices per page → allows multi-page view for container-only

Important: Backend no longer slices items when dataTypeId == null. This ensures container-only mode works with multiple pages of items.

Examples

List first page of containers created by user

getContainerTree(null, null, "0xUSERADDR", 0, 21, EnumSet.of(ContainerTreeIncludeEnum.DATA_TYPE));


Fetch all data items for container "C1", across all data types

getContainerTree("C1", null, "0xUSERADDR", 0, 21, EnumSet.of(ContainerTreeIncludeEnum.DATA_ITEM));


Returns all data items in all data types.

Frontend paginates items per 21 rows.

Fetch items for a single data type "DT1"

getContainerTree("C1", "DT1", "0xUSERADDR", 0, 21, EnumSet.of(ContainerTreeIncludeEnum.DATA_ITEM));


Returns only items of DT1.

Backend can return a single page (no lazy loading needed).

Notes

The frontend (ItemsLoader) handles slicing/pagination for data items.

The backend always returns all items for container-only view to enable multi-page pagination.

Items are grouped by data type (Map<DataTypeId, List<DataItem>>).

Supports both specific data type filtering and container-only lazy loading.

This documentation contains all details needed to reproduce the method exactly.
If you give me this, I can regenerate the working method with container-only pagination, specific data type selection, and proper grouping of data items.