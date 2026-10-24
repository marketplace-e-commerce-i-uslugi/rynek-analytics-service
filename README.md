# rynek-analytics-service

`analytics-service` — statystyki sprzedawcy

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8096, zmienna PORT
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8096/swagger-ui.html
- Zdrowie: http://localhost:8096/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 9 Panel sprzedawcy. Statystyki filmów (wyświetlenia na TikToku) pobiera z `tiktok-integration-service`.

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| POST | `/api/v1/analytics/events` | Wsadowy odbiór zdarzeń klienta (wyświetlenie oferty, odtworzenie filmu, klik „Kup teraz”) | pub |
| GET | `/api/v1/seller/analytics/overview?range=` | Kafle KPI: wyświetlenia, obejrzenia filmów, zamówienia, przychód + zmiana do poprzedniego okresu | seller |
| GET | `/api/v1/seller/analytics/timeseries?metric=&range=` | Dane do wykresu | seller |
| GET | `/api/v1/seller/analytics/top-offers?range=` | Najlepsze oferty | seller |
| GET | `/api/v1/seller/analytics/offers/{offerId}` | Statystyki jednej oferty (lejek: wyświetlenia → karta → koszyk → zakup) | seller |

## Zdarzenia Kafka

Wiersze z §17, w których występuje `analytics` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `channel.followed`, `channel.unfollowed` | channel | feed, notification, analytics |
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `order.created`, `order.paid`, `order.shipped`, `order.completed`, `order.cancelled` | order | catalog (zapas), notification, analytics, engagement |
| `booking.created`, `booking.confirmed`, `booking.cancelled`, `booking.completed` | booking | notification, analytics, engagement |

## Dane i zależności

- **Magazyn danych:** PostgreSQL lub baza kolumnowa (do decyzji)
- **Zależności:** tiktok-integration-service (statystyki filmów)
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 8. `analytics-service` — panel sprzedawcy.
