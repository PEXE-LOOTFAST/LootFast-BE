# lootfast-backend
Java Spring Boot backend for LootFast – end-of-day food deals platform

## Local development

Start the backend with the Maven Wrapper:

```bash
./mvnw spring-boot:run
```

After startup, open:

- Swagger UI: http://localhost:8080/swagger-ui
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Local API routes use the `http://localhost:8080/api/v1/...` prefix. Deployment
environments can override `SERVER_PORT` and `CONTEXT_PATH` without changing code.

## User roles

A user can have multiple roles. Every public signup starts with `BUYER`. After an
admin approves a merchant application, the account keeps `BUYER` and receives
`MERCHANT` as an additional role.

The API returns the complete `roles` array. The singular `role` field remains
temporarily for older clients; new clients should use `roles`.

There is intentionally no public endpoint for creating an admin. After the admin
has signed up and verified their email, grant the role directly in PostgreSQL:

```sql
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN'
FROM users
WHERE email = 'replace-with-admin-email@example.com'
ON CONFLICT DO NOTHING;
```

This command adds `ADMIN` without removing the account's existing `BUYER` role.
The admin must sign in again or refresh their token before calling admin APIs.

## Merchant application flow

1. Buyer sends `businessLicenseNumber` and a JPEG/PNG `file` (maximum 5 MB) to
   `POST /api/v1/merchant-applications`.
2. Buyer checks the result at `GET /api/v1/merchant-applications/me`.
3. Admin reads pending applications from
   `GET /api/v1/admin/merchant-applications?status=PENDING&page=0&size=20`.
4. Admin requests a five-minute private license link from
   `GET /api/v1/admin/merchant-applications/{id}/license-download-url`.
5. Admin approves with
   `POST /api/v1/admin/merchant-applications/{id}/approve`, or rejects with
   `POST /api/v1/admin/merchant-applications/{id}/reject` and a JSON `reason`.

License images use Cloudinary's authenticated delivery type. Unlike avatars and
merchant logos, they are not exposed through a permanent public URL.
