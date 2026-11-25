# Spec: Catalog (categories, products, variants, prices, details)

> Source: [intent.md](./intent.md)

## Overview
A REST catalog API in the existing Spring Boot app (`com.bakeby.bakebyapi`). Admin endpoints (`/api/v1/admin/**`) manage categories, products (with details and images) and variants (with prices). Public endpoints (`/api/v1/**`) expose active data, paginated. PostgreSQL via Docker Compose, schema via Flyway, soft delete via `active` flag, errors as RFC 7807 ProblemDetail. Security is fully open for now.

## Functional Requirements
1. [Must] CRUD for categories (flat; name, slug, description, active).
2. [Must] CRUD for products belonging to exactly one category, with details (description, ingredients, allergens, dietaryType, shelfLifeDays, leadTimeHours, serves) and ordered image URLs.
3. [Must] CRUD for variants under a product: label, unique SKU, price (minor units, INR), optional compare-at price, inStock flag, active.
4. [Must] Categories and products have unique slugs: auto-generated from name, optionally supplied/edited by admin.
5. [Must] Public reads return only active entities; lists are paginated; products embed active variants and images.
6. [Must] Delete is soft (`active=false`); admin can reactivate via update.
7. [Must] Validation and conflicts return ProblemDetail 400/404/409.
8. [Should] Filter public product list by category (id or slug).
9. [Could] Filter by dietaryType; sort by price (min variant price).
10. [Must] Flyway migrations; `docker-compose.yml` with Postgres wired in `application.yaml`.
11. [Must] All endpoints permitted without auth.

## Behavior
- **Slug generation:** if `slug` is absent on create, derive from `name` (lowercase, non-alphanumerics become `-`, trimmed). On collision for products, append `-2`, `-3`, ...; for categories, return 409. An explicitly supplied slug that collides returns 409. Slug pattern: `^[a-z0-9]+(?:-[a-z0-9]+)*$`. Updating `name` does not change the slug; the slug changes only when supplied.
- **Soft delete:** `DELETE` sets `active=false` and returns 204. Deleting a product deactivates its variants. Deleting a category with active products returns 409. Deleting an already inactive entity is idempotent (204).
- **Reactivation:** `PUT` with `active=true`. Reactivating a product requires its category to be active (else 409).
- **Public reads:** inactive category/product/variant yield 404 on get-by-id/slug and are omitted from lists and embeds. Products in inactive categories are not visible.
- **Variants:** a product may have zero variants (draft); the public list still shows it with empty `variants`. SKU is unique across all variants (including inactive).
- **Price:** `priceMinor` integer >= 0 (paise). `compareAtPriceMinor` optional, must be > `priceMinor` when present. Currency fixed `INR`, exposed in responses as `currency`.
- **Pagination:** `page` (0-based, default 0), `size` (default 20, max 100), `sort` limited to whitelisted fields; default sort `name,asc`.
- **Admin lists:** include inactive entities by default; `includeInactive=false` hides them.

## Interfaces
Base `/api/v1`. JSON. IDs are `Long`.

### Public (GET only)
| Endpoint | Description |
|---|---|
| `/categories` | Paged active categories |
| `/categories/{idOrSlug}` | One category (numeric = id, else slug) |
| `/products?category={idOrSlug}&dietaryType=&page=&size=&sort=` | Paged active products |
| `/products/{idOrSlug}` | Product with variants and images |

### Admin
| Endpoint | Description |
|---|---|
| `POST /admin/categories`, `GET /admin/categories`, `GET/PUT/DELETE /admin/categories/{id}` | Category CRUD |
| `POST /admin/products`, `GET /admin/products`, `GET/PUT/DELETE /admin/products/{id}` | Product CRUD |
| `POST /admin/products/{productId}/variants`, `GET /admin/products/{productId}/variants`, `GET/PUT/DELETE /admin/products/{productId}/variants/{variantId}` | Variant CRUD |

PUT is full replacement (all writable fields). POST returns 201 with a `Location` header.

### Schemas
```json
// Category
{ "id": 1, "name": "Cakes", "slug": "cakes", "description": "...", "active": true }

// Product (response)
{ "id": 10, "name": "Chocolate Truffle Cake", "slug": "chocolate-truffle-cake",
  "categoryId": 1, "active": true,
  "details": { "description": "...", "ingredients": "...",
               "allergens": ["GLUTEN", "DAIRY", "EGGS"],
               "dietaryType": "VEG", "shelfLifeDays": 2, "leadTimeHours": 24, "serves": 6 },
  "images": ["https://example.com/a.jpg"],
  "variants": [ { "id": 100, "sku": "CTC-500G", "label": "500 g", "priceMinor": 65000,
                  "compareAtPriceMinor": 75000, "currency": "INR",
                  "inStock": true, "active": true } ] }
```
Product create/update bodies take `name, slug?, categoryId, active, details, images`; variants are not accepted inline. Variant body: `sku, label, priceMinor, compareAtPriceMinor?, inStock, active`.

Enums: `Allergen` = GLUTEN, DAIRY, EGGS, NUTS, PEANUTS, SOY, SESAME. `DietaryType` = VEG, EGGLESS, VEGAN, NON_VEG.

### Errors (ProblemDetail, `application/problem+json`)
- 400: validation (with an `errors` map of field to message), malformed body or params, unknown `categoryId` in a body.
- 404: entity not found.
- 409: duplicate slug/SKU/category name, category has active products, product's category is inactive.

## Data Model
Tables (Flyway `V1__catalog.sql`):
- `category`: id PK, name varchar(100) unique not null, slug varchar(120) unique not null, description text, active bool default true, created_at, updated_at.
- `product`: id PK, category_id FK not null, name varchar(150) not null, slug varchar(170) unique not null, description text, ingredients text, dietary_type varchar(20) not null, shelf_life_days int, lead_time_hours int, serves int, active bool, created_at, updated_at. Index on category_id.
- `product_allergen`: product_id FK, allergen varchar(20); PK (product_id, allergen).
- `product_image`: product_id FK, position int, url varchar(500); PK (product_id, position).
- `variant`: id PK, product_id FK not null, sku varchar(64) unique not null, label varchar(100) not null, price_minor bigint not null check >= 0, compare_at_price_minor bigint check > price_minor, in_stock bool, active bool, created_at, updated_at. Index on product_id.

Validation: name not blank; images are valid http(s) URLs, max 10; shelfLifeDays/leadTimeHours >= 0, serves >= 1; label/sku not blank; sku pattern `^[A-Za-z0-9._-]+$`.

## Technical Design
- Packages under `com.bakeby.bakebyapi.catalog`: `category`, `product`, `variant`, each with entity, repository, service, controllers (public + admin) and DTO records; plus `common` (ProblemDetail `@RestControllerAdvice`, slug util, exceptions).
- Spring Data JPA, Lombok, Bean Validation; hand-written DTO mapping (no mapper library). `spring.jpa.hibernate.ddl-auto=validate`.
- Add dependencies: Flyway (`flyway-core` + `flyway-database-postgresql`, or the Boot 4.2 starter), validation starter, Testcontainers (postgresql + junit-jupiter, Spring Boot service connection). Verify exact Spring Boot 4.2 artifact names at implementation time.
- `SecurityFilterChain` permitting all requests, CSRF disabled (stateless API).
- `docker-compose.yml`: `postgres:17`, db/user/password `bakeby`, port 5432, named volume. `application.yaml` datasource `jdbc:postgresql://localhost:5432/bakeby` with env-var overrides (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- Product list uses fetch joins or entity graphs to avoid N+1 for variants/images; pagination applies to products, not joined rows.
- Config property `bakeby.currency=INR`.

## Non-Functional Requirements
- Performance: product list p95 < 200 ms for 1k products locally; no N+1 queries.
- Security: none enforced (explicitly open); input validated; only local dev credentials committed.
- Observability: default Spring logging; admin mutations logged at INFO.

## Edge Cases & Error Handling
- Unknown `categoryId` on product create/update -> 400 with field error on `categoryId`.
- Duplicate SKU -> 409. Duplicate category name or slug -> 409.
- compareAt <= price -> 400. Negative price -> 400.
- `size` > 100 -> clamped to 100. Unknown sort field -> 400.
- Variant path where `productId` does not own `variantId` -> 404.
- A numeric value in a public `{idOrSlug}` path is treated as an id (slugs must therefore contain a letter; the slug pattern check rejects all-digit slugs).
- Concurrent duplicate creates -> DB unique violation mapped to 409.

## Acceptance Criteria
- [ ] Given an empty DB, when admin POSTs a category, a product (with details/images) and a variant, then each returns 201 and a public GET of the product shows all of it.
- [ ] Given a product created without slug, then a slug is derived from its name; a second product with the same name gets a `-2` suffix.
- [ ] Given a soft-deleted product, when GET public by id/slug, then 404 and it is absent from the list; admin GET still returns it.
- [ ] Given a category with an active product, when admin deletes the category, then 409.
- [ ] Given a duplicate SKU, when creating a variant, then 409 ProblemDetail.
- [ ] Given an invalid body (blank name, negative price, compareAt <= price), then 400 with field errors.
- [ ] Given 25 products, when GET `/products?size=10&page=2`, then 5 items and correct page metadata.
- [ ] After `docker compose up -d`, the app starts, Flyway applies migrations, and unauthenticated requests succeed.

## Testing Plan
- Integration tests (`@SpringBootTest` + MockMvc + Testcontainers Postgres) covering every acceptance criterion and the endpoint matrix.
- Unit tests for slug generation and price validation.
- Optional manual smoke via Postman CLI against the local compose DB.

## Out of Scope
- Authentication/authorization, image upload, price history, multi-currency, nested categories, cart, orders, inventory counts, payments, variant slugs, full-text search.

## Open Questions
- Sort by price (a Could) needs a min-variant-price query; confirm whether it is wanted this iteration.
