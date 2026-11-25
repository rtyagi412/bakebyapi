# Intent: Catalog (categories, products, variants, prices, details)

## Summary
Implement the catalog functionality in the Bakeby backend (Spring Boot): admin CRUD for categories, products, variants (with prices) and product details, plus public read endpoints for storefront browsing.

## Problem / Goal
The backend has no domain code yet. A catalog is the foundation for everything else (cart, orders). Goal: bakery items can be created and maintained, and browsed via a REST API.

## Users & Stakeholders
- Admin/bakery staff: create and maintain categories, products, variants, prices, details.
- Storefront/clients: list and view active categories and products with variants, prices and details.

## Scope
**In scope**
- Categories: flat list; each product belongs to exactly one category.
- Products with details: description, ingredients, allergens (list), image URLs (ordered, URLs only), dietary flags (veg/egg-less/vegan), shelf life, lead time, serves.
- Variants per product (size/weight/flavor options), each with its own SKU, price and in-stock flag.
- Prices: one current price per variant, stored in minor units, single currency (configured, e.g. INR); optional compare-at/sale price.
- Admin CRUD endpoints for all of the above; public read endpoints (list, get by id).
- Soft delete via active flag; paginated list endpoints.
- Flyway migrations, DTOs with validation, Postgres-based tests.

**Out of scope**
- Authentication/authorization (all endpoints fully permitted for now; Spring Security config opened up).
- Image upload/storage, price history, multi-currency, nested categories.
- Cart, orders, inventory quantities, payments.

## Key Requirements
- [Must] CRUD for categories, products, variants; product details stored with product.
- [Must] Variant has unique SKU, price (minor units), in-stock flag, belongs to one product.
- [Must] Public read endpoints return only active entities, paginated.
- [Must] Soft delete (active flag) instead of hard delete.
- [Must] Request validation with consistent error responses (400/404/409 e.g. duplicate SKU/category name).
- [Should] Filter product list by category.
- [Should] Flyway-managed schema.
- [Could] Filter by dietary flags; sort by price.

## Constraints
- Java 25, Spring Boot 4.2, Spring Data JPA, PostgreSQL, Lombok, Maven; package `com.bakeby.bakebyapi`.
- Money as integer minor units (no floating point).
- Add `docker-compose.yml` (Postgres) and wire datasource in application.yaml.
- Add Flyway and Testcontainers dependencies (not yet in pom.xml); DB connection config not yet present in application.yaml.

## Success Criteria
- Admin can create a category, a product in it with details, and variants with prices via REST.
- Public GET endpoints list/fetch active products with variants, prices and details, paginated.
- Soft-deleted entities disappear from public reads but remain in DB.
- Validation and conflict cases return proper 4xx errors.
- Integration tests against real Postgres pass; app starts with Flyway applying migrations.

## Assumptions & Risks
- Security is intentionally open; must be locked down before any real deployment (write endpoints are public).
- Spring Boot 4.x module/package changes may affect starter and test setup.

## Decisions
- Currency is INR.
- API prefix is `/api/v1` (public reads at `/api/v1/...`, admin writes under `/api/v1/admin/...`).
- Products (and categories) have unique URL slugs; public reads can fetch by slug as well as id.
- Add a `docker-compose.yml` running Postgres for local dev; `application.yaml` datasource points at it.
- Variants have no slugs; they are identified by unique SKU.
