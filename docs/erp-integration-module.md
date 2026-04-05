# ERP Integration Module (Backend)

## Goal

Provide a standalone ERP integration surface that:

- stages off-chain ERP data independently
- publishes selected records on-chain through CLI
- tracks publish/sync lifecycle
- returns potential verification follow-ups

All ERP persistence is isolated into `erp_*` tables only.

## Package Layout

`src/main/java/com/easypublish/erp`

- `controller/ErpIntegrationController`
- `service/ErpIntegrationService`
- `service/ErpRecordService`
- `service/ErpPublishService`
- `service/ErpVerificationService`
- `service/ErpCliService`
- `service/ErpSecurityService`
- `entities/*`
- `repositories/*`
- `dto/*`

## API Root

`/api/erp`

Integration-scoped endpoints require either:

- `ownerAddress` query param, or
- `X-ERP-API-KEY` header

Security default:

- `app.erp.api.allow-owner-address-auth=false`
- with this default, integration-scoped endpoints require `X-ERP-API-KEY`

## API Enablement

ERP API controller is disabled unless explicitly enabled:

```properties
app.erp.api.enabled=true
app.erp.api.allow-owner-address-auth=false
```

Default in `application.properties` is `false`.

## Core Tables

- `erp_integration`
- `erp_record`
- `erp_blob`
- `erp_publish_job`
- `erp_sync_cursor`
- `erp_verification_candidate`

## Functional Areas

- Integration management (defaults, CLI config, API keys)
- ERP record staging (single + bulk)
- Record utilities (`check`, `compact`, `zip`, `unzip`)
- CLI publish jobs (`dryRun`, retry, diagnostics)
- Sync checks/waiting state until on-chain index catches up
- Verification candidate refresh + status management

## Sync Strategy

After CLI publish success, jobs enter `WAITING_SYNC`.
Sync attempts match `erp_record.external_record_id` to on-chain `DataItem.externalId`.
When matched:

- job moves to `SYNCED`
- record moves to `PUBLISHED`
- linked data item ID is stored in ERP record

## Config (application.properties)

- `app.erp.cli.binary`
- `app.erp.cli.script`
- `app.erp.cli.working-directory`
- `app.erp.cli.default-network`
- `app.erp.cli.private-key-env-var`
- `app.erp.cli.timeout-ms`
- `app.node.cli-dir`

Per-integration settings override these defaults when present.
