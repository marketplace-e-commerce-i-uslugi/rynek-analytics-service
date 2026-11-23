package pl.rynek.analytics.stats;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.analytics.clients.CatalogClient.OfferRef;
import pl.rynek.analytics.clients.SalesClient;
import pl.rynek.analytics.clients.SalesClient.Sale;
import pl.rynek.analytics.events.EventService;
import pl.rynek.analytics.events.EventService.EventType;
import pl.rynek.analytics.stats.StatsDtos.Funnel;
import pl.rynek.analytics.stats.StatsDtos.Kpi;
import pl.rynek.analytics.stats.StatsDtos.Money;
import pl.rynek.analytics.stats.StatsDtos.Overview;
import pl.rynek.analytics.stats.StatsDtos.Point;
import pl.rynek.analytics.stats.StatsDtos.TopOffer;

/**
 * Statystyki kanału sprzedawcy. Dni liczymy w strefie Europe/Warsaw. Okres to ostatnie 7, 30 albo 91 dni (do dziś włącznie), porównywany
 * z poprzednim okresem o tej samej długości; wykres ma słupek na dzień, a dla kwartału na tydzień.
 * <ul>
 *   <li>{@code views}: oferta pojawiła się na liście (OFFER_IMPRESSION), {@code videoViews}: film pokazany (VIDEO_PLAY);</li>
 *   <li>{@code orders}, {@code revenue}: sprzedaż z order-service (opłacone, nieanulowane; wartość towarów) i booking-service (wykonane usługi).</li>
 * </ul>
 */
@Service
public class StatsService {

    static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("d.MM");

    /** {@code days} dni okresu, {@code bucket} dni w słupku wykresu. */
    private record Range(String key, int days, int bucket) {
    }

    private record Period(Range range, LocalDate currentStart, LocalDate previousStart, LocalDate endExclusive) {
        Instant from() {
            return previousStart.atStartOfDay(ZONE).toInstant();
        }

        Instant to() {
            return endExclusive.atStartOfDay(ZONE).toInstant();
        }
    }

    private final JdbcTemplate jdbc;
    private final SalesClient sales;
    private final EventService events;
    private final Clock clock;
    private final int topOffers;
    private final int funnelDays;

    public StatsService(JdbcTemplate jdbc, SalesClient sales, EventService events, Clock clock,
                        @Value("${rynek.analytics.top-offers}") int topOffers, @Value("${rynek.analytics.funnel-days}") int funnelDays) {
        this.jdbc = jdbc;
        this.sales = sales;
        this.events = events;
        this.clock = clock;
        this.topOffers = topOffers;
        this.funnelDays = funnelDays;
    }

    public Overview overview(UUID channel, String range) {
        Period p = period(range);
        List<Sale> sold = sales.sales(channel, p.from(), p.to());
        List<Kpi> kpis = new ArrayList<>();
        for (String metric : List.of("views", "videoViews", "orders", "revenue")) {
            Map<LocalDate, Long> daily = daily(channel, metric, p, sold);
            long current = sum(daily, p.currentStart(), p.range().days());
            long previous = sum(daily, p.previousStart(), p.range().days());
            boolean money = metric.equals("revenue");
            kpis.add(new Kpi(metric, label(metric), money ? Math.round(current / 100.0) : current, change(current, previous),
                    money ? new Money(current, "PLN") : null));
        }
        return new Overview(p.range().key(), kpis);
    }

    public List<Point> timeseries(UUID channel, String metric, String range) {
        Period p = period(range);
        label(metric);
        List<Sale> sold = metric.equals("orders") || metric.equals("revenue") ? sales.sales(channel, p.from(), p.to()) : List.of();
        Map<LocalDate, Long> daily = daily(channel, metric, p, sold);
        boolean money = metric.equals("revenue");
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < p.range().days() / p.range().bucket(); i++) {
            int offset = i * p.range().bucket();
            LocalDate start = p.currentStart().plusDays(offset);
            long current = sum(daily, start, p.range().bucket());
            long previous = sum(daily, p.previousStart().plusDays(offset), p.range().bucket());
            points.add(new Point(LABEL.format(start), money ? Math.round(current / 100.0) : current, money ? Math.round(previous / 100.0) : previous));
        }
        return points;
    }

    public List<TopOffer> topOffers(UUID channel, String range) {
        Period p = period(range);
        return jdbc.query("select offer_id, count(*) as n from analytics_events where channel_id = ? and type = ? and occurred_at >= ? and occurred_at < ? "
                        + "group by offer_id order by n desc, offer_id limit ?",
                (rs, i) -> new TopOffer(rs.getObject("offer_id", UUID.class), null, rs.getLong("n")),
                channel, EventType.OFFER_IMPRESSION.name(), Timestamp.from(p.currentStart().atStartOfDay(ZONE).toInstant()), Timestamp.from(p.to()), topOffers)
                .stream().map(t -> new TopOffer(t.offerId(), title(t.offerId()), t.views())).toList();
    }

    /** Lejek własnej oferty z ostatnich {@code funnel-days} dni; oferta cudza albo nieistniejąca to 404. */
    public Funnel funnel(UUID channel, UUID offerId) {
        OfferRef offer = events.offer(offerId).filter(o -> o.channelId().equals(channel))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty."));
        Instant to = clock.instant();
        Instant from = LocalDate.now(clock.withZone(ZONE)).minusDays(funnelDays - 1L).atStartOfDay(ZONE).toInstant();
        Map<EventType, Long> counts = new EnumMap<>(EventType.class);
        jdbc.query("select type, count(*) as n from analytics_events where offer_id = ? and occurred_at >= ? and occurred_at <= ? group by type",
                rs -> {
                    counts.put(EventType.valueOf(rs.getString("type")), rs.getLong("n"));
                }, offerId, Timestamp.from(from), Timestamp.from(to));
        long purchases = sales.sales(channel, from, to.plusSeconds(1)).stream().filter(s -> s.offerIds().contains(offerId)).count();
        return new Funnel(offerId, offer.title(), counts.getOrDefault(EventType.OFFER_IMPRESSION, 0L), counts.getOrDefault(EventType.OFFER_VIEW, 0L),
                counts.getOrDefault(EventType.ADD_TO_CART, 0L) + counts.getOrDefault(EventType.BUY_NOW_CLICK, 0L), purchases);
    }

    // --- pomocnicze ---

    private String title(UUID offerId) {
        try {
            return events.offer(offerId).map(OfferRef::title).orElse("Oferta");
        } catch (ResponseStatusException e) {
            return "Oferta";
        }
    }

    private Period period(String range) {
        Range r = switch (range == null || range.isBlank() ? "30d" : range) {
            case "7d" -> new Range("7d", 7, 1);
            case "30d" -> new Range("30d", 30, 1);
            case "quarter" -> new Range("quarter", 91, 7);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany okres. Wybierz 7d, 30d albo quarter.");
        };
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate currentStart = today.minusDays(r.days() - 1L);
        return new Period(r, currentStart, currentStart.minusDays(r.days()), today.plusDays(1));
    }

    private static String label(String metric) {
        return switch (metric) {
            case "views" -> "Wyświetlenia";
            case "videoViews" -> "Obejrzenia filmów";
            case "orders" -> "Zamówienia";
            case "revenue" -> "Przychód (towary)";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznana miara. Wybierz views, videoViews, orders albo revenue.");
        };
    }

    /** Wartość miary na każdy dzień okresu (poprzedni + bieżący); przychód w groszach. */
    private Map<LocalDate, Long> daily(UUID channel, String metric, Period p, List<Sale> sold) {
        Map<LocalDate, Long> daily = new HashMap<>();
        switch (metric) {
            case "views" -> countEvents(channel, EventType.OFFER_IMPRESSION, p, daily);
            case "videoViews" -> countEvents(channel, EventType.VIDEO_PLAY, p, daily);
            case "orders" -> sold.forEach(s -> daily.merge(day(s.at()), 1L, Long::sum));
            default -> sold.forEach(s -> daily.merge(day(s.at()), s.amount(), Long::sum));
        }
        return daily;
    }

    private void countEvents(UUID channel, EventType type, Period p, Map<LocalDate, Long> into) {
        jdbc.query("select (occurred_at at time zone 'Europe/Warsaw')::date as d, count(*) as n from analytics_events "
                        + "where channel_id = ? and type = ? and occurred_at >= ? and occurred_at < ? group by 1",
                rs -> {
                    into.put(rs.getObject("d", LocalDate.class), rs.getLong("n"));
                }, channel, type.name(), Timestamp.from(p.from()), Timestamp.from(p.to()));
    }

    private static LocalDate day(Instant at) {
        return at.atZone(ZONE).toLocalDate();
    }

    private static long sum(Map<LocalDate, Long> daily, LocalDate start, int days) {
        long total = 0;
        for (int i = 0; i < days; i++) {
            total += daily.getOrDefault(start.plusDays(i), 0L);
        }
        return total;
    }

    /** Zmiana procentowa; od zera do czegokolwiek to +100%, a zero do zera to 0. */
    private static int change(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100 : 0;
        }
        return (int) Math.round((current - previous) * 100.0 / previous);
    }
}
