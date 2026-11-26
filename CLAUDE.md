# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Bakeby backend: a Spring Boot 4.2 REST API (Java 25, Maven, Spring Data JPA, PostgreSQL, Lombok), base package `com.bakeby.bakebyapi`. The code is at an early stage: only `BakebyapiApplication` and a context-load test exist. The first feature, the catalog (categories, products, variants, prices), is specified but not yet built.

## Commands

Use the Maven wrapper (on Windows PowerShell: `.\mvnw.cmd`).

- Build/compile: `./mvnw compile`
- Run the app: `./mvnw spring-boot:run`
- All tests: `./mvnw test` (full check: `./mvnw verify`)
- Single test class: `./mvnw test -Dtest=BakebyapiApplicationTests`
- Single test method: `./mvnw test -Dtest=ClassName#methodName`

There is no separate lint step configured. The plan calls for `docker compose up -d` (Postgres 17) before running the app; `docker-compose.yml` does not exist yet.

## Spec-driven workflow

Features are developed spec-first under `specs/<NNN>-<slug>/` with three files created by the project skills in `.claude/skills/`: `intent.md` (`/intent`), `spec.md` (`/spec`), `plan.md` (`/plan`). Read the relevant spec and plan before implementing. `specs/001-catalog/plan.md` has phased tasks (1.1, 1.2, ...) with dependencies and "done when" checks, and a traceability table mapping them to the spec's FR/AC numbers.

Three subagents in `.claude/agents/` split the work: `implementer` (writes code, not the tests), `tester` (writes and runs tests, edits only test files, reports implementation bugs), and `reviewer` (read-only). They do not call each other, so run them in that order.

## Catalog design decisions (from specs/001-catalog)

These are decided and not obvious from code yet:

- Money is integer minor units (`priceMinor`, `compareAtPriceMinor`), never floating point. Currency is a single configured value (`bakeby.currency=INR`).
- Soft delete via an `active` flag only. Public reads show active entities only. Deleting a product deactivates its variants. A category cannot be deleted while it has active products (409), and a product cannot be reactivated under an inactive category.
- Categories and products have unique slugs. Public `/{idOrSlug}` lookups treat a numeric value as an id, so slugs that are all digits are rejected.
- API prefix is `/api/v1`: public reads at `/api/v1/...`, admin writes at `/api/v1/admin/...`. Variants are nested at `/api/v1/admin/products/{productId}/variants` and identified by unique SKU.
- Spring Security is deliberately opened (permit all, CSRF off) until auth is built. This must be locked down before any real deployment.
- Errors use `ProblemDetail` through one `@RestControllerAdvice`: 400 validation (with an `errors` map), 404, and 409 (including `DataIntegrityViolationException`).
- Schema is managed by Flyway (`db/migration/V1__catalog.sql`) with `ddl-auto=validate`. Tests run against Testcontainers Postgres, not H2.
- Public product lists must paginate without N+1 queries or in-memory pagination of collection fetch joins, so use an entity graph/batch fetching or page the product IDs first.

## Coding conventions

These apply to all code in this repo. Subagents must follow them.

**Layering and structure**

- Package by feature under `com.bakeby.bakebyapi.<feature>` (e.g. `catalog`), with one subpackage per kind of class: `controller` (REST controllers), `dto` (request and response records), `service`, `repository`, `entity` (JPA entities and enums) and `mapper` (MapStruct). Cross-cutting code goes in `common`, split the same way: `common.exception` (exceptions and the global handler), `common.config` (Spring config and `@ConfigurationProperties`), `common.dto` (shared response types such as `PageResponse`) and `common.util` (helpers such as `SlugUtil`, `PageRequests`).
- Test classes live in the same package as the class they test.
- Controllers are thin: validate, call one service method, return a DTO. Business rules and `@Transactional` live in the service layer, not in controllers or repositories.
- Constructor injection only, via Lombok `@RequiredArgsConstructor` with `final` fields. No field `@Autowired`.

**DTOs and mapping**

- Request and response DTOs are Java `record`s. Put Bean Validation annotations (`@NotBlank`, `@Size`, `@PositiveOrZero`, ...) on the record components, and `@Valid` on controller parameters.
- Never expose JPA entities in API requests or responses.
- Use MapStruct for entity/DTO mapping (`@Mapper(componentModel = "spring")`), not hand-written mapping code. Do not map in controllers.
- Keep separate create, update and response records. Do not reuse one DTO for all three.

**Entities and Lombok**

- Entities are classes, not records (JPA needs a no-arg constructor and mutability). Use `@Getter`, `@Setter`, `@NoArgsConstructor` and `@Builder` as needed.
- Do not use `@Data` or `@EqualsAndHashCode` on entities, and keep collections and lazy associations out of `@ToString`. Equality is based on the id or a business key, not on all fields.
- Use Lombok `@Slf4j` for logging. No `System.out` or `printStackTrace`.

**Exception handling**

- One global `@RestControllerAdvice` in `common.exception` returns `ProblemDetail` for every error. It is the only place that builds error responses.
- Services throw domain exceptions (`NotFoundException`, `ConflictException`, ...) that extend a common base. Controllers and services never catch exceptions only to build a response, and never return error bodies themselves.
- Map exceptions to status in the advice: validation to 400 (with an `errors` map), not found to 404, conflicts and `DataIntegrityViolationException` to 409, and a catch-all to 500 that logs the exception and returns no stack trace or internals.

**Persistence**

- Schema changes go in a new Flyway migration (`V<n>__<name>.sql`). Never edit an applied migration. `ddl-auto` stays `validate`.
- Repositories extend Spring Data interfaces. Avoid N+1 queries, and do not return unbounded lists from public endpoints (paginate).

**Config and style**

- Configuration comes from `application.yaml` and env overrides, bound with `@ConfigurationProperties` or `@Value`. No secrets or hard-coded environment values in code.
- Prefer `Optional` returns from lookups over `null`. No new dependencies without a reason.

**Tests**

- Integration tests use Testcontainers Postgres, never H2. Name tests for the behavior they check (`returnsConflictWhenSlugExists`). Cover happy path, validation failure, not found and conflict for each endpoint.

## Environment notes

- Spring Boot 4.x changed starter names and packages (the pom uses `spring-boot-starter-webmvc`, `...-data-jpa-test`, `...-webmvc-test`). Verify artifact names for Flyway, validation and Testcontainers by compiling when you add them.
- Lombok runs through explicit `annotationProcessorPaths` in the `maven-compiler-plugin` executions in `pom.xml`. Keep Lombok registered there when changing compiler config.
- MapStruct (when added) must also be in `annotationProcessorPaths`, listed after Lombok, together with `lombok-mapstruct-binding`. Without that order, mappers generate empty or the build fails. Check generated mappers under `target/generated-sources` after adding it.
- `application.yaml` currently sets only the app name. The plan adds datasource settings with `DB_URL`, `DB_USER` and `DB_PASSWORD` env overrides.
