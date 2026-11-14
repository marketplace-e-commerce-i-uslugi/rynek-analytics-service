package pl.rynek.analytics.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.analytics.clients.CatalogClient;
import pl.rynek.analytics.clients.CatalogClient.OfferRef;

/**
 * Odbiór zdarzeń klienta. Każde zdarzenie dostaje kanał sprzedawcy oferty (z catalog, z krótką pamięcią podręczną), więc statystyki
 * kanału to jedno zapytanie po {@code channel_id}. Zdarzenia o nieistniejących ofertach są odrzucane po cichu: odbiór jest publiczny.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final int MAX_CACHE = 20_000;
    private static final Duration MAX_PAST = Duration.ofDays(1);
    private static final Duration MAX_FUTURE = Duration.ofMinutes(5);

    public enum EventType { OFFER_IMPRESSION, OFFER_VIEW, VIDEO_PLAY, ADD_TO_CART, BUY_NOW_CLICK }

    public record EventInput(EventType type, UUID offerId, Instant at) {
    }

    private record Cached(Optional<OfferRef> offer, Instant fetchedAt) {
    }

    private final JdbcTemplate jdbc;
    private final CatalogClient catalog;
    private final Clock clock;
    private final Duration ttl;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public EventService(JdbcTemplate jdbc, CatalogClient catalog, Clock clock, @Value("${rynek.analytics.offer-cache-ttl}") Duration ttl) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** Oferta (kanał i tytuł) z pamięci podręcznej albo z catalog; nieistniejąca też jest zapamiętana, żeby losowe id nie męczyły catalog. */
    public Optional<OfferRef> offer(UUID offerId) {
        Instant now = clock.instant();
        Cached cached = cache.get(offerId);
        if (cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
            return cached.offer();
        }
        Optional<OfferRef> fresh = catalog.offer(offerId);
        if (cache.size() >= MAX_CACHE) {
            cache.clear();
        }
        cache.put(offerId, new Cached(fresh, now));
        return fresh;
    }

    /** Zapisuje zdarzenia znanych ofert; zwraca liczbę zapisanych. Czas klienta poza [-1 dzień, +5 min] zastępujemy czasem serwera. */
    public int record(Optional<UUID> user, List<EventInput> events) {
        Instant now = clock.instant();
        List<Object[]> rows = new ArrayList<>();
        for (EventInput e : events) {
            if (e.offerId() == null) {
                continue;
            }
            Optional<OfferRef> offer;
            try {
                offer = offer(e.offerId());
            } catch (ResponseStatusException ex) {
                log.warn("Pominięto zdarzenie {} oferty {}: {}", e.type(), e.offerId(), ex.getReason());
                continue;
            }
            if (offer.isEmpty()) {
                continue;
            }
            Instant at = e.at() != null && e.at().isAfter(now.minus(MAX_PAST)) && e.at().isBefore(now.plus(MAX_FUTURE)) ? e.at() : now;
            rows.add(new Object[]{UUID.randomUUID(), e.type().name(), e.offerId(), offer.get().channelId(), user.orElse(null), Timestamp.from(at)});
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("insert into analytics_events (id, type, offer_id, channel_id, user_id, occurred_at) values (?, ?, ?, ?, ?, ?)", rows);
        }
        return rows.size();
    }
}
