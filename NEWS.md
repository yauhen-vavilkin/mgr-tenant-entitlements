## Version `v4.1.0` (In Progress)
* Integration with Apache APISIX: gateway selection via `APIGW_TYPE` (`kong` default, `apisix`), APISIX admin key via
  `APIGW_API_KEY`; route stages now use the gateway-agnostic `ApiGatewayService` from applications-poc-tools.
  Switching a live environment's type does not backfill existing entitlements' routes, and state previously
  written to the old gateway is not cleaned up — drain it manually or remove it from the traffic path (MGRENTITLE-173)
* Gracefully complete entitlement for desired state operation in no async mode (MGRENTITLE-161)
* Upgrade dependencies for Kafka 4.2 compatibility in mgr-tenant-entitlements (MGRENTITLE-180)
* Switch Kong integration test container to folioci/folio-kong image (APPPOCTOOL-37)
* **Breaking:** Remove "Okapi integration" mode. `OKAPI_INTEGRATION_ENABLED`, `OKAPI_URL` and
  `MOD_AUTHTOKEN_URL` are no longer supported; the Okapi entitle/revoke/upgrade flows and the
  `OkapiModulesInstaller` stage are gone, leaving the folio module flow as the only path, and
  mod-authtoken security mode is no longer reachable. The `okapi.proxy.tenants.install.post`
  subPermissions were dropped from the module descriptor (MGRENTITLE-111)
* Remove the application count limit from reinstall endpoint (MGRENTITLE-172)
* Include the full module ID in system-user Kafka events for downstream entitlement processing tracking (MGRENTITLE-192)
* Move Kong mgmt logic from mgr-applications to mgr-tenant-entitlements (MGRENTITLE-174)
* Async Entitlement processing feedback loop - Kafka Processing (MGRENTITLE-158)
* Support desired-state entitlement validation for entitle, upgrade, and revoke transitions (MGRENTITLE-186)
* Deprecate the Kong-specific gateway configuration in favour of the API Gateway naming (EUREKA-887)
* [Kong] Module routes are incorrectly assigned to multiple gateway services (MGRENTITLE-197)
* Filter wildcard permissionsRequired entries from mgr-tenant-entitlements capability warnings (MGRENTITLE-182)
* Accept a tenant collection name (e.g. `ALL`) in `KAFKA_PRODUCER_TENANT_COLLECTION`, as other FOLIO modules do (MGRENTITLE-202)
* Establish flow ownership for entitlement operations: each MTE instance generates and logs a unique instance ID at
  startup and stamps it as `owner_instance_id` of every root flow it creates, so recovery can distinguish active
  flows from ones abandoned by a crashed or restarted process (MGRENTITLE-188)

---

## Version `4.0.0` (16.04.2026)
* Not able to entitle application due to dependency issue (MGRENTITLE-118)
* mgr-tenant-entitlements does not check the cross-application module dependencies as expected (MGRENTITLE-113)
* Introduce optional application dependencies (MGRAPPS-57)
* Introduce configuration for FSSP (APPPOCTOOL-59)
* Reinstall endpoint responding with 403 error (MGRENTITLE-134)
* Validate that upgrade of application does not affect other installed applications (MGRENTITLE-68)
* Concurrent Kafka topic creation at application flow level causes intermittent errors (MGRENTITLE-135)
* Use SECURE_STORE_ENV, not ENV, for secure store key (MGRENTITLE-139)
* Add support for custom Keycloak base URL for JWKS endpoint, new ENV variable `KC_JWKS_BASE_URL` (MODSIDECAR-148)
* Implement automatic token refresh for long-running operations to prevent token expiration issues (MGRENTITLE-141)
* Implement Kafka Tenant Collection Topics (MGRENTITLE-41)
* Implement configurable thread pool for module installation to control entitlement concurrency independently from main flow engine (MGRENTITLE-141)
* Implement Desired State Management for Application Entitlements in MTE (MGRENTITLE-140)
* Review and clean up logs in mgr-tenant-entitlements (MGRENTITLE-152)
* Half of timers are disabled after recent bugfest update for 1 tenant (MGRENTITLE-165)
* Migrate CI to centralized FOLIO Maven GitHub workflow (MGRENTITLE-162)
* Upgrade module to SpringBoot4.0 and Spring7.0 (MGRENTITLE-148)
* Adopt APPPOCTOOL-85 Kafka producer/common module split and shared resource event types (APPPOCTOOL-85)

---

## Version `v3.1.0` (09.04.2025)
* Reinstall API endpoints (MGRENTITLE-103)fix
* Permission mappings for mod-patron-blocks (MGRENTITLE-107)
* Enable security by default (MGRENTITLE-104)

---

## Version `v3.0.0` (11.03.2025)
* Upgrade Java to v21. (MGRENTITLE-102)
* Remove Kong routes management for entitled modules (MGRENTITLE-92)
* Extend GET /entitlements with ability to retrieve tenant entitlements by tenant name via additional "tenant" query parameter (MGRENTITLE-101)

---

## Version `v2.0.0` (01.11.2024)
* Remove routes while purge=false (MGRENTITLE-75)
* Implement retries for external calls (Kong, Keycloak, FOLIO Modules) (MGRENTITLE-72)
* Increase keycloak-admin-client to v25.0.6 (KEYCLOAK-24)

---

## Version `v1.3.0` (30.09.2024)
* Disable tenant matching validation (APPPOCTOOL-27)
* Move system user publisher stage before module installer… (MODCONSKC-7)
* Use folio-auth-openid library for JWT validation (APPPOCTOOL-28)

---

## Version `v1.2.4` (14.08.2024)
* Use metadata field instead of removed extensions (MGRENTITLE-63)
* Adjust test after resource creation filter updated (APPPOCTOOL-25)
* Implement application version upgrades for capability events (MODROLESKC-200)
* Improve documentation for entitlement query parameters (MGRENTITLE-38)
* Use extensions field to generate system user events (MGRAPPS-23)
* Fix ApplicationFlowValidator to forbid installing lower versions (MGRENTITLE-62)

---

## Version `v1.2.3` (10.07.2024)
* upgrade kong version (KONG-10)

---

## Version `v1.2.1` (20.06.2024)
* extract SemverUtils to folio-common (MODSCHED-8)
* mgr-tenant-entitlements (RANCHER-1502)
* Configuration parameter names fixed.
* Added TLS support for FolioClientConfigurationProperties

---

## Version `v1.2.0` (25.05.2024)
* Keycloak client: support TLS certificates issued by trusted certificate authorities (MGRENTITLE-54)
* add HTTPS access to application-manager (MGRENTITLE-52)
* Implement upgrade operation for modules in folio flow (MGRENTITLE-51)
* add HTTPS access to mgr-tenants (MGRENTITLE-48)
* add HTTPS access to Kong (MGRENTITLE-43)
* Create a docker file for the mgr-tenant-entitlements module that is based on the FIPS-140-2 compliant base image (ubi9/openjdk-17-runtime) (MGRENTITLE-42)
* Implement support for upgrade operation (MGRENTITLE-39)
* Secure mgr-tenant-entitlements HTTP end-points with SSL (MGRENTITLE-37)
* Implement upgrade event for system users (MGRENTITLE-23)
* Implement upgrade event for Capability entity (MGRENTITLE-22)
* Implement upgrade for scheduled job event (MGRENTITLE-21)
* Implement upgrade operation for Kong routes (MGRENTITLE-20)

---

## Version `v1.1.0` (16.04.2024)
* Detached module entities are tried to update with null values (MGRENTITLE-40)
* Include timer interface endpoint into account in integration test asserts (EUREKA-66)
* Implement upgrade operation for Keycloak service (MGRENTITLE-19)
* update Keycloak-related tests (APPPOCTOOL-10)
* Kong timeouts should be extended (KONG-6)
