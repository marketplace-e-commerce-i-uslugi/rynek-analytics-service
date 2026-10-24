package pl.rynek.analytics.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.analytics.common.NotImplemented.notImplemented;

@RestController
public class SellerAnalyticsController {

    /**
     * Kafle KPI: wyświetlenia, obejrzenia filmów, zamówienia, przychód + zmiana do poprzedniego okresu. Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/overview")
    public void getOverview(@RequestParam(required = false) String range) {
        throw notImplemented();
    }

    /**
     * Dane do wykresu. Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/timeseries")
    public void getTimeseries(@RequestParam(required = false) String metric, @RequestParam(required = false) String range) {
        throw notImplemented();
    }

    /**
     * Najlepsze oferty. Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/top-offers")
    public void getTopOffers(@RequestParam(required = false) String range) {
        throw notImplemented();
    }

    /**
     * Statystyki jednej oferty (lejek: wyświetlenia → karta → koszyk → zakup). Dostęp: seller.
     */
    @GetMapping("/api/v1/seller/analytics/offers/{offerId}")
    public void getOfferStats(@PathVariable String offerId) {
        throw notImplemented();
    }
}
