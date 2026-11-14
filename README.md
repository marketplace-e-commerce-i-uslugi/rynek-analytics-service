# rynek-analytics-service

`analytics-service` — statystyki sprzedawcy

> Status: **działa** (PostgreSQL, Flyway, JWT z JWKS, bez Kafki). Zdarzenia klienta zapisuje sam, sprzedaż czyta z order-service i booking-service (`GET /internal/analytics/sales`), kanał i tytuł oferty z catalog (`/internal/offers/{id}/channel`).

- **Zdarzenia** (`POST /analytics/events`, pub, do 50 w żądaniu, 204): `OFFER_IMPRESSION` (oferta pojawiła się na liście, kafel widoczny w połowie), `OFFER_VIEW` (otwarta karta), `VIDEO_PLAY` (film pokazany: w Shortach i w galerii oferty), `ADD_TO_CART`, `BUY_NOW_CLICK`. Zdarzenia o nieistniejących ofertach są odrzucane po cichu, czas klienta poza [-1 dzień, +5 min] zastępuje czas serwera. Frontend zbiera zdarzenia i wysyła paczką co ~2 s (`core/analytics/event-tracker.ts`), a wyświetlenie kafla i film liczy raz na ofertę w sesji strony.
- **Okres** `range`: `7d`, `30d` (domyślnie), `quarter` (91 dni); dni w strefie Europe/Warsaw, do dziś włącznie, porównanie z poprzednim okresem o tej samej długości (zmiana w %: od zera do czegokolwiek +100, zero do zera 0). Wykres ma słupek na dzień, a dla `quarter` na tydzień.
- **Miary:** `views` = `OFFER_IMPRESSION`, `videoViews` = `VIDEO_PLAY`, `orders` i `revenue` = sprzedaż: zamówienia opłacone i nieanulowane (chwila wejścia w „Do wysłania”, wartość towarów bez dostawy) oraz usługi oznaczone jako wykonane (termin usługi, cena). Przychód w KPI w złotych (`money` w groszach), na wykresie w złotych.
- **Najlepsze oferty:** do 5 według wyświetleń w okresie. **Lejek oferty** obejmuje ostatnie 90 dni: wyświetlenia → karta → koszyk (`ADD_TO_CART` + `BUY_NOW_CLICK`) → zakup (zamówienia i wykonane usługi z tą ofertą). Cudza albo nieistniejąca oferta daje 404.
- **Bez:** limitów zapytań po stronie zdarzeń (poza limitem gatewaya), retencji (zdarzenia rosną bez końca), statystyk filmów z TikToka (liczymy odtworzenia w Rynku) i usług jeszcze niewykonanych w przychodzie. Gdy order albo booking nie odpowiada, przegląd, przychód i lejek dają 503 (liczby bez niego byłyby mylące), a wyświetlenia i najlepsze oferty działają.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8096, zmienna PORT; baza `analytics` z rynek-infra/local
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
