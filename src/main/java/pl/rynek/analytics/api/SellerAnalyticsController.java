package pl.rynek.analytics.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.analytics.clients.ChannelClient;
import pl.rynek.analytics.stats.StatsDtos.Funnel;
import pl.rynek.analytics.stats.StatsDtos.Overview;
import pl.rynek.analytics.stats.StatsDtos.Point;
import pl.rynek.analytics.stats.StatsDtos.TopOffer;
import pl.rynek.analytics.stats.StatsService;

/** Statystyki kanału zalogowanego sprzedawcy (kanał to ten, który należy do konta z tokenu). */
@RestController
public class SellerAnalyticsController {

    private final StatsService stats;
    private final ChannelClient channels;

    public SellerAnalyticsController(StatsService stats, ChannelClient channels) {
        this.stats = stats;
        this.channels = channels;
    }

    /**
     * Kafle KPI: wyświetlenia, obejrzenia filmów, zamówienia, przychód + zmiana do poprzedniego okresu ({@code range}: 7d, 30d, quarter).
     * Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/overview")
    public Overview getOverview(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String range) {
        return stats.overview(channel(token), range);
    }

    /**
     * Dane do wykresu: {@code metric} to views, videoViews, orders albo revenue (złote); słupek na dzień, dla quarter na tydzień. Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/timeseries")
    public List<Point> getTimeseries(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String metric,
                                     @RequestParam(required = false) String range) {
        return stats.timeseries(channel(token), metric == null ? "views" : metric, range);
    }

    /**
     * Najlepsze oferty okresu według wyświetleń (najwyżej 5). Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/top-offers")
    public List<TopOffer> getTopOffers(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String range) {
        return stats.topOffers(channel(token), range);
    }

    /**
     * Statystyki jednej oferty z ostatnich 90 dni (lejek: wyświetlenia → karta → koszyk → zakup). Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/offers/{offerId}")
    public Funnel getOfferStats(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        UUID id;
        try {
            id = UUID.fromString(offerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty.");
        }
        return stats.funnel(channel(token), id);
    }

    private UUID channel(Jwt token) {
        return channels.channelOf(UUID.fromString(token.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Najpierw załóż kanał."));
    }
}
