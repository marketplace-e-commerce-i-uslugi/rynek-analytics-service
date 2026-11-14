# rynek-analytics-service

Mikroserwis platformy **Rynek**: `analytics-service` — statystyki sprzedawcy

Zakres, endpointy i zdarzenia są opisane w `README.md`. Wszystkie endpointy działają (zdarzenia klienta we własnej bazie, sprzedaż z order i booking przez REST). Nie dodawaj Kafki bez wyraźnej prośby.

## Wspólne konwencje API (§1 dokumentu architektury)

| Temat | Zasada |
|---|---|
| Prefiks | `/api/v1/...`, gateway kieruje po prefiksie ścieżki |
| Auth | JWT Bearer wystawiany przez `identity-service`, walidowany w gateway i w serwisach (Spring Security Resource Server) |
| Role | `BUYER`, `SERVICE_PROVIDER`, `MERCHANT`, `ADMIN`; jeden użytkownik może mieć kilka |
| Paginacja | kursorowa: `?cursor=...&limit=20` → `{items, nextCursor}` |
| Kwoty | liczba całkowita w groszach + waluta: `{"amount": 38900, "currency": "PLN"}` |
| Błędy | RFC 7807 (`application/problem+json`) |
| Idempotencja | nagłówek `Idempotency-Key` na `POST /orders`, `POST /payments`, `POST /bookings` |
| Język | `Accept-Language: pl-PL`, treści błędów po polsku |
| Ścieżki wewnętrzne | `/internal/...` — tylko między serwisami, nieudostępnione przez gateway |
| Zdarzenia | Kafka, nazwy `domena.zdarzenie` (sekcja 17) |

Legenda kolumny **Dostęp**: `pub` = bez logowania, `user` = zalogowany, `seller` = właściciel kanału (usługodawca lub merchant), `admin`, `int` = wewnętrzny.

## Stack

- Java 25, Spring Boot 4.1 (Maven, `./mvnw`)
- Dane: PostgreSQL lub baza kolumnowa (do decyzji)
- Zdarzenia: Kafka, nazwy `domena.zdarzenie`

## Powiązane repozytoria

- `rynek-docs`: pełny dokument architektury, design system, koncepcja produktu
- `rynek-contracts`: OpenAPI i schematy zdarzeń (źródło prawdy dla interfejsów między serwisami)
- `rynek-infra`: lokalne środowisko i wdrożenia

## Struktura kodu

- `pl.rynek.analytics.api`: kontrolery REST (pełne ścieżki w adnotacjach); `events`: odbiór i zapis zdarzeń (`EventService`, pamięć podręczna ofert); `stats`: `StatsService` (okresy, KPI, wykres, lejek) i `StatsDtos`; `clients`: channel, catalog, sprzedaż (order i booking); `config`: Spring Security (JWT z JWKS), `Viewer`, błędy RFC 7807
- Konfiguracja: `src/main/resources/application.yml`
- Port lokalny: 8096
