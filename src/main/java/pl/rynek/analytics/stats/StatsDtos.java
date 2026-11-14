package pl.rynek.analytics.stats;

import java.util.List;
import java.util.UUID;

/** Kształty zgodne z rynek-frontend/src/app/core/api/models/analytics.ts. */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record Money(long amount, String currency) {
    }

    /** {@code value}: liczba (przychód w złotych); {@code change}: zmiana procentowa względem poprzedniego okresu o tej samej długości. */
    public record Kpi(String key, String label, long value, int change, Money money) {
    }

    public record Overview(String range, List<Kpi> kpis) {
    }

    public record Point(String label, long current, long previous) {
    }

    public record TopOffer(UUID offerId, String title, long views) {
    }

    public record Funnel(UUID offerId, String title, long views, long detailViews, long addedToCart, long purchases) {
    }
}
