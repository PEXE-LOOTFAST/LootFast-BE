# AGENTS.md — LootFast Backend

## Project overview
LootFast is a real-world end-of-day food discount platform. This repository contains the backend service.

Core stack:
- Java 21
- Spring Boot
- Maven Wrapper
- PostgreSQL
- Docker / Docker Compose
- H2 for automated tests
- GitHub Actions CI
- REST API
- Swagger / OpenAPI

## Primary AI rule
Do not blindly generate code.

Before a meaningful change:
1. Inspect the existing implementation.
2. Explain the problem or requirement.
3. Propose a short implementation plan.
4. Identify files that will change.
5. Only then modify code.

For small, obvious fixes, keep the explanation short.

The goal is both to build a production-quality backend and help the developer understand backend engineering.

## Git safety
Never:
- commit directly to `main` or `dev`;
- force-push shared branches;
- run `git reset --hard` or `git clean -fd` without explicit permission;
- delete branches or rewrite history without explicit permission;
- merge Pull Requests automatically;
- commit `.env`, secrets, tokens, passwords, or production credentials;
- bypass failing CI just to merge.

Before destructive Git operations, ask for confirmation.

## Branch strategy
Long-lived protected branches:
- `main`
- `dev`

Create working branches from the latest `dev`:

```bash
git switch dev
git pull origin dev
git switch -c feature/<name>
```

Branch naming:
- `feature/<name>`
- `fix/<name>`
- `chore/<name>`
- `refactor/<scope>`
- `test/<scope>`
- `docs/<scope>`

Examples:
- `feature/authentication`
- `feature/deal-management`
- `fix/database-configuration`
- `chore/docker-file-setup`

Before starting work:

```bash
git status
git branch --show-current
```

Do not overwrite unrelated uncommitted changes.

## Commit convention
Use Conventional Commits:
- `feat:`
- `fix:`
- `chore:`
- `refactor:`
- `test:`
- `docs:`
- `build:`
- `ci:`

Examples:
- `feat: add deal creation endpoint`
- `fix: configure H2 datasource for tests`
- `chore: add Docker development environment`
- `ci: add backend verification workflow`

Before committing:

```bash
git status
git diff
```

Stage only relevant files when possible:

```bash
git add <relevant-files>
git diff --cached
git commit -m "fix: configure H2 datasource for tests"
```

Do not commit automatically unless explicitly requested.

## Pull Request flow
Normal flow:

```text
working branch
    ↓
push
    ↓
Pull Request
    ↓
dev
    ↓
CI
    ↓
review
    ↓
merge
```

Normal PR target: `dev`.

Before opening or updating a PR:

```bash
./mvnw clean verify
```

Merge only when:
- CI is green;
- tests pass;
- code was reviewed;
- no secrets or accidental files exist;
- requested changes are resolved.

Prefer **Squash and merge** for normal feature/fix/chore PRs into `dev` when the PR represents one logical change.

After merge:

```bash
git switch dev
git pull origin dev
```

Do not delete branches automatically.

## CI rules
GitHub Actions is a quality gate.

Expected flow:
1. Checkout source.
2. Set up JDK.
3. Prepare Maven Wrapper.
4. Build.
5. Run tests.
6. Verify.

CI command:

```bash
./mvnw --batch-mode clean verify
```

When CI fails:
1. find the first meaningful error;
2. inspect the deepest useful `Caused by:`;
3. reproduce locally if possible;
4. fix on the same PR branch;
5. run tests;
6. push;
7. let CI rerun.

Do not create another branch just to fix the currently open PR unless there is a specific reason.

## Maven rules
Use the project Maven Wrapper.

Linux:
```bash
./mvnw clean verify
```

Windows:
```powershell
.\mvnw.cmd clean verify
```

Useful commands:
- `clean` — remove old build artifacts;
- `test` — run tests;
- `verify` — compile, test, and run Maven verification;
- `spring-boot:run` — start the application.

## Java / Spring Boot architecture
Prefer standard Spring Boot conventions.

Typical flow:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Suggested feature packages as the system grows:
- `common`
- `config`
- `auth`
- `user`
- `buyer`
- `merchant`
- `deal`
- `order`
- `payment`
- `subscription`

A feature may contain:
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `mapper`
- `exception`

Do not create empty layers merely for appearance.

### Controllers
Controllers should:
- receive HTTP requests;
- validate request DTOs;
- call services;
- return appropriate responses.

Avoid complex business logic or direct database access in controllers.

### Services
Services should:
- implement use cases;
- enforce business rules;
- coordinate repositories;
- define transactions where appropriate.

### Repositories
Use Spring Data JPA where appropriate.
Keep repositories focused on persistence.
Do not put business logic in repositories.

### DTOs
Prefer request/response DTOs instead of exposing JPA entities directly.

Examples:
- `CreateDealRequest`
- `UpdateDealRequest`
- `DealResponse`

Use Jakarta Bean Validation where appropriate.

### Entities
Be careful with:
- bidirectional relationships;
- cascades;
- eager loading;
- JSON serialization loops;
- generated `equals`, `hashCode`, and `toString`.

Do not add Lombok annotations blindly to entities.

## Database rules
Runtime database: PostgreSQL.
Local infrastructure: Docker.
Simple automated tests: H2 in-memory database.

Do not hard-code a PostgreSQL driver or Hibernate dialect when Spring Boot can infer them from the datasource URL.

Production-style URL:

```text
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
```

Test URL:

```text
jdbc:h2:mem:lootfast;MODE=PostgreSQL
```

Test configuration belongs under `src/test/resources`.

Simple tests should not depend on the developer's local PostgreSQL container.

For PostgreSQL-specific integration behavior, consider Testcontainers later, but explain the trade-offs before introducing it.

## Environment variables
Runtime configuration should use environment variables such as:
- `APP_NAME`
- `SERVER_PORT`
- `CONTEXT_PATH`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `PGADMIN_EMAIL`
- `PGADMIN_PASSWORD`
- `PGADMIN_PORT`

Local values may be stored in `.env`.

The repository may contain `.env.example`, but never commit real secrets.

Whenever code introduces, renames, or removes an environment variable:
- update both the local `.env` and the tracked `.env.example` in the same task;
- put the real local value only in `.env`;
- put an empty value or safe documented placeholder in `.env.example`;
- never copy API secrets, tokens, passwords, private keys, or production credentials
  into `.env.example`;
- verify that `.env` remains ignored by Git and inspect the staged diff for secret
  leakage before finishing;
- restart the application and verify that Spring Boot actually resolves the new
  configuration, because `.env` changes are loaded at startup.

## application.yaml rules
Prefer environment-driven configuration.

Example:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Avoid forcing `driver-class-name` or a Hibernate dialect unless actually necessary.

## Context path and API versioning
Do not confuse application context path with API versioning.

`CONTEXT_PATH` may be empty in local/test environments.

Prefer explicit API versioning in routes:
- `/api/v1/deals`
- `/api/v1/orders`
- `/api/v1/merchants`

Do not automatically set `CONTEXT_PATH=/api/v1` unless the architecture explicitly chooses that approach.

## Docker rules
Docker is used for local infrastructure.

Before debugging database connectivity:

```bash
docker ps
```

When modifying Docker configuration:
- preserve persistent volumes;
- avoid unnecessary exposed services;
- keep configuration environment-driven;
- never commit credentials.

Do not delete Docker volumes without explicit permission.

## Testing rules
Preserve the basic `contextLoads` test.

As features grow, add meaningful:
- unit tests;
- repository tests;
- controller tests;
- integration tests.

Before finishing a coding task:

```bash
./mvnw clean verify
```

must pass unless there is a clearly documented unrelated reason.

## Error handling
Prefer centralized API error handling as the project grows.

Possible concepts:
- `GlobalExceptionHandler`
- `BusinessException`
- `ResourceNotFoundException`
- validation error handling

Keep API error responses consistent.

## Logging
Use application logging instead of `System.out.println()`.

Never log:
- passwords;
- access tokens;
- refresh tokens;
- payment secrets;
- sensitive personal information.

Use appropriate log levels:
- DEBUG
- INFO
- WARN
- ERROR

## Security
For authentication/authorization:
- never store plain-text passwords;
- enforce authorization server-side;
- do not trust user-supplied roles;
- do not expose sensitive security internals.

Never weaken security just to make development easier without clearly marking it as temporary.

## Payment rules
Payment code is high-risk.

When implementing payments:
1. inspect the existing flow;
2. explain transaction state changes;
3. validate callback authenticity;
4. ensure idempotency where necessary;
5. never trust frontend-reported payment success;
6. preserve transaction/audit information.

## Swagger / OpenAPI
Keep endpoint definitions and DTO schemas consistent.
Ensure Swagger still starts after API changes.
Inspect `application.yaml` instead of assuming the exact Swagger URL.

## Dependency rules
Before adding a Maven dependency:
1. explain why it is needed;
2. check whether Spring Boot already provides the capability;
3. verify compatibility with the current Spring Boot / Spring Framework version;
4. avoid duplicate libraries;
5. prefer maintained libraries.

Do not silently change major dependency versions.

## Version compatibility gate
At the start of every task, inventory the versions of affected runtime components
and verify that they are mutually compatible before changing code. At minimum,
check the relevant Java, Spring Boot, Maven Wrapper, PostgreSQL image, Flyway,
JDBC driver, Redis image, and Testcontainers versions.

Keep each runtime service on the same major version across Docker Compose,
integration tests, CI, and documentation. Never start an existing PostgreSQL data
volume with an older major-version image. A PostgreSQL major-version migration
requires an explicit backup/restore or `pg_upgrade` plan and user approval.

Flyway migrations are immutable after they have run in any shared or developer
database. Never edit an applied migration to fix or refine it; restore its original
checksum-compatible contents and add a new forward-only migration instead.

Before finishing every task, run the full Maven verification and, when database or
runtime behavior is affected, start the application against the declared real
PostgreSQL major version. Report the observed version matrix and startup result.

## Code quality
Prefer code that is:
- simple;
- readable;
- maintainable;
- testable.

Avoid:
- premature microservices;
- unnecessary design patterns;
- unnecessary generic abstractions;
- giant classes/methods;
- duplicated business logic;
- hidden side effects;
- unnecessary dependencies.

Do not introduce Redis, Kafka, RabbitMQ, Kubernetes, microservices, etc. merely because they are common backend technologies. Introduce them only when LootFast has a concrete problem that requires them.

## Production readiness and concurrency
For every meaningful feature or infrastructure change, perform a production-readiness
review proportional to its risk before declaring it complete.

Check at minimum:
- thread safety and race conditions when multiple requests update the same resource;
- transaction boundaries, locking strategy, idempotency, and database constraints;
- database query count, indexes, pagination, connection-pool usage, and N+1 queries;
- bounded request/file sizes, timeouts, retries with backoff, and failure isolation for
  external services;
- rate limiting or abuse protection where an endpoint is expensive or externally billed;
- memory, CPU, thread, and connection consumption under concurrent traffic;
- safe horizontal scaling: do not rely on process-local state for shared correctness;
- structured logging, metrics, health checks, and actionable error handling;
- backward-compatible database migrations and deployment/rollback behavior;
- security, privacy, secret handling, and least-privilege access;
- unit, integration, concurrency, and load tests appropriate to the expected traffic.

Do not claim that a feature can safely handle a large number of users merely because
functional tests pass. Define an expected traffic profile and measurable targets, run
representative load tests in a production-like environment, inspect bottlenecks and
resource saturation, and document the tested capacity and remaining limits. If those
inputs or an appropriate environment are unavailable, clearly report that production
capacity has not yet been proven.

## AI coding workflow
For implementation tasks:

### 1. Inspect
Read relevant code, configuration, tests, and `pom.xml`.

### 2. Explain
Briefly state:
- current state;
- problem;
- proposed solution;
- files affected.

### 3. Plan
Describe implementation steps.

### 4. Implement
Make the smallest coherent change.

### 5. Verify
Run:

```bash
./mvnw clean verify
```

### 6. Review
Show or inspect:

```bash
git diff
git status
```

Summarize:
- files changed;
- behavior added;
- tests executed;
- unresolved concerns.

## Learning-oriented explanations
The primary developer is learning backend Java.

When introducing an important concept, briefly explain:
- what it is;
- why it is needed;
- how it applies to LootFast.

Examples:
- `@Transactional`
- Dependency Injection
- JPA Entity
- Repository
- DTO
- Bean Validation
- JWT
- Filter
- Interceptor
- database transaction
- lazy loading
- caching
- message queue

Do not merely say “best practice”; explain the concrete reason.

## Command explanation rule
Whenever suggesting shell, Maven, Docker, or Git commands, briefly explain what each command does.

Never suggest destructive commands without explaining their effect.

## Debugging rule
For long Java/Spring stack traces, do not treat every line as a separate error.

Find the root cause, usually through `Caused by:`.

Explain the causal chain and fix root causes, not symptoms.

## Warnings vs errors
Distinguish among:
- compilation errors;
- test failures;
- runtime errors;
- security problems;
- warnings;
- IDE inspections.

A red/yellow IDE inspection does not automatically mean the Maven build fails.

## Before finishing any task
When appropriate, check:

```bash
git status
git diff
./mvnw clean verify
```

Final report should state:
- what changed;
- why;
- files changed;
- build/test result;
- remaining issues.

Do not claim a task is complete when relevant tests are failing.

## Final principle
Act as a senior backend engineer collaborating with a junior backend developer.

Desired workflow:

```text
AI investigates
      ↓
AI explains
      ↓
Developer understands
      ↓
AI implements with permission
      ↓
Tests verify
      ↓
Developer reviews diff
      ↓
PR + CI
      ↓
Merge
```

Optimize for correctness, maintainability, learning, and safe collaboration.
