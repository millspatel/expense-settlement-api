# Group Expense Settlement API

A Spring Boot REST API for sharing expenses within groups — sign up, create groups, add expenses split
**equally, by percentage or by exact amounts**, and see who owes whom. A **debt simplification**
algorithm reduces a tangle of IOUs to the fewest practical payments.

![CI](../../actions/workflows/ci.yml/badge.svg)

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA (Hibernate) ·
MySQL 8 (H2 for local/tests) · JUnit 5 + MockMvc · Docker · GitHub Actions · OpenAPI/Swagger UI

---

## Debt simplification — the interesting part

> A owes B ₹100 and B owes C ₹100 → simplified to **A pays C ₹100**.

`core/DebtSimplifier.java`

1. Collapse every expense and settlement into one **net balance** per person
   (`paid − consumed`; positive = is owed, negative = owes). Balances always sum to zero.
2. Put creditors and debtors into two **max-heaps**.
3. Repeatedly match the **largest debtor with the largest creditor** and transfer `min(debt, credit)`.
   At least one side hits zero each round, so the result has **at most n − 1 payments**, in `O(n log n)`.

Finding the absolute minimum number of payments is NP-hard (it reduces to subset-sum), so this greedy
approach is what production apps use. A randomized test checks 500 random groups to confirm every result
settles all balances exactly within the n − 1 bound.

`GET /api/groups/{id}/balances` returns both the raw `pairwiseDebts` and the `simplifiedDebts`, so you can
see the reduction side by side.

### Money handling

All splitting is done in whole paise (`long`), never `double`. ₹100 split 3 ways becomes
`33.34 / 33.33 / 33.33`; percentage splits use the largest-remainder method. Shares **always** add up to the
exact total (`core/SplitCalculator.java`).

---

## API

All endpoints except signup/login require `Authorization: Bearer <token>`.
Interactive docs: **`/swagger-ui.html`** once the app is running.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/signup` | Create an account → JWT |
| POST | `/api/auth/login` | Log in → JWT |
| GET | `/api/users/me` | Current user |
| GET | `/api/users/me/balance` | Your balance in each group + overall total |
| POST | `/api/groups` | Create a group (optionally with `memberEmails`) |
| GET | `/api/groups` | Groups you belong to |
| GET | `/api/groups/{id}` | Group details and members |
| POST | `/api/groups/{id}/members` | Add a member by email |
| POST | `/api/groups/{id}/expenses` | Add an expense (EQUAL / PERCENTAGE / EXACT) |
| GET | `/api/groups/{id}/expenses` | Expense history, newest first |
| POST | `/api/groups/{id}/settlements` | Record a payment ("settle up") |
| GET | `/api/groups/{id}/settlements` | Settlement history |
| GET | `/api/groups/{id}/balances` | Net balances, pairwise debts, simplified debts |

Errors use RFC 7807 `application/problem+json`, with per-field messages for validation failures.

### Example

```bash
# sign up (repeat for bob@example.com and carol@example.com)
curl -s -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'
TOKEN=<token from response>

# create a group
curl -s -X POST localhost:8080/api/groups -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Goa trip","memberEmails":["bob@example.com","carol@example.com"]}'

# equal split across all members (omit "splits")
curl -s -X POST localhost:8080/api/groups/1/expenses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"Dinner","amount":900,"splitType":"EQUAL"}'

# percentage split
curl -s -X POST localhost:8080/api/groups/1/expenses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"Hotel","amount":6000,"splitType":"PERCENTAGE",
       "splits":[{"userId":1,"value":50},{"userId":2,"value":25},{"userId":3,"value":25}]}'

# exact amounts, paid by someone else
curl -s -X POST localhost:8080/api/groups/1/expenses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"Cab","amount":450,"paidByUserId":2,"splitType":"EXACT",
       "splits":[{"userId":1,"value":200},{"userId":3,"value":250}]}'

# who owes whom
curl -s localhost:8080/api/groups/1/balances -H "Authorization: Bearer $TOKEN"
```

---

## Running it

**Locally (in-memory H2, no setup):**

```bash
mvn spring-boot:run
```

**With MySQL via Docker:**

```bash
docker compose up --build
```

**Tests:**

```bash
mvn test
```

### Configuration

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:mysql://host:3306/db` | in-memory H2 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | DB credentials | `sa` / empty |
| `JWT_SECRET` | Base64-encoded HMAC key, ≥ 32 bytes. **Set this in production.** | dev-only key |
| `JWT_EXPIRATION_MINUTES` | Token lifetime | `1440` |
| `PORT` | HTTP port | `8080` |

Generate a secret with `openssl rand -base64 48`.

### Deploying

1. Create a **MySQL 8** database (e.g. a MySQL service on Railway, or Aiven / any managed MySQL)
   and note its host, port, database, user and password.
2. Deploy this repo as a **Docker** web service (Railway, Render, etc.).
3. Set environment variables: `SPRING_DATASOURCE_URL=jdbc:mysql://<host>:<port>/<db>`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `JWT_SECRET`.
4. Health check path: `/actuator/health`.

---

## Project layout

```
src/main/java/com/splitwise
├── core/          SplitCalculator, DebtSimplifier — pure algorithms, no Spring
├── model/         JPA entities: User, ExpenseGroup, Expense, ExpenseSplit, Settlement
├── repository/    Spring Data repositories (fetch-joined queries to avoid N+1)
├── service/       Business logic + membership checks
├── controller/    REST endpoints
├── security/      JWT issue/verify, auth filter, Spring Security config
├── dto/           Request/response records with Bean Validation
└── exception/     Problem+JSON error handling
```
