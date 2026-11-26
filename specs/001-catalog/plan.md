# Plan: Catalog (categories, products, variants, prices, details)

> Source: [spec.md](./spec.md)

## Summary

Build bottom-up: infrastructure first (Postgres via Docker Compose, Flyway, dependencies, open security, error handling), then the schema, then each aggregate in dependency order (category, product, variant), then the public read API, and finally edge-case hardening and end-to-end verification. Every phase leaves the app buildable, and integration tests run against Testcontainers Postgres from Phase 1 onward.

## Assumptions & Prerequisites

- Existing code is only `BakebyapiApplication.java`, `application.yaml` (app name only) and the default `BakebyapiApplicationTests` (a context-load test that will need a database once JPA and Flyway are active).
- JDK 25, Docker Desktop available locally (Compose and Testcontainers).
- Exact Spring Boot 4.2 artifact names for Flyway, validation and Testcontainers are verified on first build (Task 1.1).
- Sort-by-price (spec FR 9, a "Could") is scheduled as the last, optional task.

## Phase 1: Foundation

**Goal:** The app boots against a local Postgres with Flyway, open security and a shared error format; tests run on Testcontainers.

- [x] **1.1 Dependencies**
  - Do: In `pom.xml` add the validation starter, Flyway (`flyway-core` + `flyway-database-postgresql`, or the Boot 4.2 starter), and Testcontainers (`postgresql`, `junit-jupiter`, Spring Boot Testcontainers support) in test scope.
  - Covers: FR 10; spec Technical Design.
  - Depends on: none
  - Done when: `./mvnw -q compile` succeeds.
- [x] **1.2 Docker Compose and datasource config**
  - Do: Create `docker-compose.yml` (`postgres:17`, db/user/password `bakeby`, port 5432, named volume). In `application.yaml` set datasource (`DB_URL`, `DB_USER`, `DB_PASSWORD` env overrides with local defaults), `ddl-auto=validate`, Flyway enabled, `bakeby.currency=INR`.
  - Covers: FR 10; AC 8.
  - Depends on: 1.1
  - Done when: `docker compose up -d` makes Postgres reachable on `localhost:5432`.
- [x] **1.3 Open security config**
  - Do: Create `config/SecurityConfig` with a `SecurityFilterChain` that permits all requests and disables CSRF.
  - Covers: FR 11; AC 8.
  - Depends on: 1.1
  - Done when: a test hitting any path without credentials gets a non-401/403 response.
- [x] **1.4 Test infrastructure**
  - Do: Create a shared Testcontainers Postgres configuration (service connection) and an abstract integration-test base. Update `BakebyapiApplicationTests` to use it.
  - Covers: Testing Plan.
  - Depends on: 1.1
  - Done when: `./mvnw test` passes with the context loading against a container (before any migration, an empty-baseline migration or Flyway with no scripts allowed).
- [x] **1.5 Common error handling and utilities**
  - Do: Under `catalog/common`: `NotFoundException`, `ConflictException`, `@RestControllerAdvice` mapping to ProblemDetail (400 validation with `errors` map, 400 malformed body/params, 404, 409, and `DataIntegrityViolationException` to 409); `SlugUtil` (slugify, pattern, reject all-digit slugs); pagination helper (default 20, max 100, whitelisted sort fields, unknown sort returns 400).
  - Covers: FR 7; spec Behavior (slug, pagination); edge cases on size clamping and unknown sort.
  - Depends on: 1.1
  - Done when: unit tests for `SlugUtil` and for the advice (via a test controller) pass.

## Phase 2: Schema

**Goal:** The database schema exists and entities validate against it.

- [x] **2.1 Flyway migration**
  - Do: Create `src/main/resources/db/migration/V1__catalog.sql` with `category`, `product`, `product_allergen`, `product_image`, `variant`, including unique constraints, checks (price >= 0, compare_at > price), FKs and indexes per the spec Data Model.
  - Covers: FR 10; spec Data Model.
  - Depends on: 1.2, 1.4
  - Done when: the app/test context starts and Flyway applies V1 to the container.
- [x] **2.2 Entities and repositories**
  - Do: Create JPA entities `Category`, `Product` (embedded details, `@ElementCollection` allergens as enum set and ordered images), `Variant`, enums `Allergen` and `DietaryType`, timestamps, and Spring Data repositories with `ddl-auto=validate` passing.
  - Covers: FR 1 to 3, 6; spec Data Model.
  - Depends on: 2.1
  - Done when: a `@DataJpaTest`/integration test saves and reloads each entity and schema validation passes.

## Phase 3: Categories

**Goal:** Category admin CRUD works end to end.

- [x] **3.1 Category service and DTOs**
  - Do: Create request/response records with validation, `CategoryService` with slug generation (409 on collision), duplicate-name 409, soft delete blocked (409) when active products exist, idempotent delete.
  - Covers: FR 1, 4, 6, 7.
  - Depends on: 2.2, 1.5
  - Done when: service tests pass for create, slug, duplicate name, delete-with-active-products.
- [x] **3.2 Admin category controller**
  - Do: Create `AdminCategoryController` at `/api/v1/admin/categories` (POST 201 + Location, GET list with `includeInactive`, GET/PUT/DELETE by id).
  - Covers: FR 1, 6, 7; AC 4, 6.
  - Depends on: 3.1
  - Done when: MockMvc integration tests cover each endpoint and error status.

## Phase 4: Products

**Goal:** Product admin CRUD with details and images works end to end.

- [x] **4.1 Product service and DTOs**
  - Do: Create DTOs (`details`, `images`, validation: URL format, max 10, non-negative numbers, serves >= 1), `ProductService` with slug auto-generation and `-2`/`-3` suffixing, explicit-slug collision 409, category existence check (400 on `categoryId`), reactivation requires an active category (409), delete cascades to deactivate variants.
  - Covers: FR 2, 4, 6, 7; AC 2, 6.
  - Depends on: 3.1
  - Done when: service tests cover slug suffixing, bad category, reactivation rule, cascade deactivate.
- [x] **4.2 Admin product controller**
  - Do: Create `AdminProductController` at `/api/v1/admin/products` (POST, GET list with `includeInactive`, GET/PUT/DELETE). Responses embed variants (all, in admin) and images; fetch strategy avoids N+1.
  - Covers: FR 2, 6, 7; AC 1, 6.
  - Depends on: 4.1
  - Done when: MockMvc tests pass for CRUD, validation errors and soft delete.

## Phase 5: Variants and prices

**Goal:** Variants with prices are managed under a product.

- [x] **5.1 Variant service and DTOs**
  - Do: Create `VariantService`: unique SKU (409), `priceMinor >= 0`, `compareAtPriceMinor > priceMinor`, SKU pattern, product ownership check (404 when `variantId` is not under `productId`), soft delete, `currency` read from `bakeby.currency`.
  - Covers: FR 3, 6, 7; AC 5, 6.
  - Depends on: 4.1
  - Done when: service tests cover duplicate SKU, price rules, ownership 404.
- [x] **5.2 Admin variant controller**
  - Do: Create `AdminVariantController` at `/api/v1/admin/products/{productId}/variants` (POST, GET list, GET/PUT/DELETE by id).
  - Covers: FR 3, 6, 7; AC 1, 5, 6.
  - Depends on: 5.1, 4.2
  - Done when: MockMvc tests pass; a variant created here appears in the admin product response.

## Phase 6: Public read API

**Goal:** Storefront endpoints return only active data, paginated.

- [ ] **6.1 Public category endpoints**
  - Do: Create `CategoryController` with `GET /api/v1/categories` (paged) and `/{idOrSlug}` (numeric = id, else slug; inactive gives 404).
  - Covers: FR 5; AC 3.
  - Depends on: 3.1
  - Done when: MockMvc tests cover list, by id, by slug, inactive 404.
- [ ] **6.2 Public product endpoints**
  - Do: Create `ProductController` with `GET /api/v1/products` (paged; filters `category` by id or slug, `dietaryType`) and `/{idOrSlug}`. Show only active products in active categories, embedding only active variants and images. Use an entity graph/fetch join so pagination applies to products without N+1.
  - Covers: FR 5, 8, 9 (dietary filter); AC 1, 3, 7.
  - Depends on: 4.2, 5.2
  - Done when: tests with 25 products verify `size=10&page=2` returns 5 items with correct page metadata; inactive products/variants are hidden; a query-count check shows no N+1.

## Phase 7: Hardening and verification

**Goal:** All edge cases and acceptance criteria are proven end to end.

- [ ] **7.1 Edge-case tests**
  - Do: Add tests for: size clamped to 100, unknown sort 400, malformed JSON 400, concurrent duplicate create (unique violation to 409), numeric-slug rejection, product with zero variants shown publicly with empty `variants`, deleting an already-inactive entity (204).
  - Covers: spec Edge Cases & Error Handling; FR 7.
  - Depends on: Phases 3 to 6
  - Done when: the new tests pass.
- [ ] **7.2 End-to-end acceptance suite**
  - Do: Add one integration test class that walks AC 1 to 8 in order against real Postgres (create category, product, variant, public read, soft delete, 409 cases).
  - Covers: AC 1 to 8.
  - Depends on: 7.1
  - Done when: `./mvnw verify` is green.
- [ ] **7.3 Manual smoke (optional)**
  - Do: Run `docker compose up -d`, start the app (`./mvnw spring-boot:run`), and exercise the admin and public endpoints with the Postman CLI or curl.
  - Covers: AC 8.
  - Depends on: 7.2
  - Done when: unauthenticated create and read round trip works against the compose DB.
- [ ] **7.4 Sort by price (optional, Could)**
  - Do: Add `sort=price,asc|desc` on the public product list using the minimum active variant price (query/projection), and document it in the whitelisted sort fields.
  - Covers: FR 9.
  - Depends on: 6.2
  - Done when: a test shows products ordered by lowest variant price, with pagination still correct.

## Traceability

| Spec item | Task(s)                                |
| --------- | -------------------------------------- |
| FR 1      | 2.2, 3.1, 3.2                          |
| FR 2      | 2.2, 4.1, 4.2                          |
| FR 3      | 2.2, 5.1, 5.2                          |
| FR 4      | 1.5, 3.1, 4.1                          |
| FR 5      | 6.1, 6.2                               |
| FR 6      | 2.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2      |
| FR 7      | 1.5, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 7.1 |
| FR 8      | 6.2                                    |
| FR 9      | 6.2 (dietary), 7.4 (price sort)        |
| FR 10     | 1.1, 1.2, 2.1                          |
| FR 11     | 1.3                                    |
| AC 1      | 4.2, 5.2, 6.2, 7.2                     |
| AC 2      | 4.1, 7.2                               |
| AC 3      | 6.1, 6.2, 7.2                          |
| AC 4      | 3.1, 3.2, 7.2                          |
| AC 5      | 5.1, 5.2, 7.2                          |
| AC 6      | 3.2, 4.1, 4.2, 5.1, 5.2, 7.2           |
| AC 7      | 6.2, 7.2                               |
| AC 8      | 1.2, 1.3, 7.2, 7.3                     |

## Risks & Mitigations

- Spring Boot 4.2 artifact and package changes (Flyway, Testcontainers, validation, Jackson) -> resolved in 1.1 by compiling and running a context-load test before any feature code.
- Pagination combined with collection fetch joins can load everything in memory -> 6.2 paginates product IDs first (or uses batch fetching) and the plan includes a query-count check.
- The default context-load test breaks once a datasource is required -> 1.4 moves it onto Testcontainers immediately.
- Open security exposes write endpoints -> documented as intentional in the spec; lock down in a follow-up feature before deployment.
- Numeric slugs clash with id lookup -> slug validation rejects all-digit slugs (1.5).

## Verification

Run `docker compose up -d`, then `./mvnw verify`. The full test suite (unit, MockMvc integration on Testcontainers Postgres, and the AC 1 to 8 acceptance class) must pass, and the app must boot with Flyway applying V1 against the compose database, with unauthenticated requests succeeding.

## Open Questions

- Sort by price is planned as optional Task 7.4; confirm whether it should be included in this iteration.
