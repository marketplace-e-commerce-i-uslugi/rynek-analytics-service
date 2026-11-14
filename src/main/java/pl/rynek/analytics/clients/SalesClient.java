package pl.rynek.analytics.clients;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Sprzedaż kanału: zamówienia opłacone (order-service, {@code at} to chwila opłacenia, kwota to wartość towarów bez dostawy) i usługi
 * oznaczone jako wykonane (booking-service, {@code at} to termin usługi). Zamiast zdarzeń {@code order.*} i {@code booking.*}.
 */
@Component
public class SalesClient {

    private final RestClient orders;
    private final RestClient bookings;

    public SalesClient(@Value("${rynek.order.url}") String orderUrl, @Value("${rynek.booking.url}") String bookingUrl) {
        this.orders = Http.client(orderUrl);
        this.bookings = Http.client(bookingUrl);
    }

    /** Sprzedaż w {@code [from, to)} z obu serwisów; któryś niedostępny to wyjątek 503 (liczby bez niego byłyby mylące). */
    public List<Sale> sales(UUID channelId, Instant from, Instant to) {
        List<Sale> result = new ArrayList<>();
        for (JsonNode o : fetch(orders, channelId, from, to)) {
            Set<UUID> offerIds = new LinkedHashSet<>();
            o.path("lines").forEach(l -> offerIds.add(UUID.fromString(l.path("offerId").asString())));
            result.add(new Sale(Instant.parse(o.path("at").asString()), o.path("amount").asLong(), List.copyOf(offerIds)));
        }
        for (JsonNode b : fetch(bookings, channelId, from, to)) {
            result.add(new Sale(Instant.parse(b.path("at").asString()), b.path("amount").asLong(), List.of(UUID.fromString(b.path("offerId").asString()))));
        }
        return result;
    }

    private static JsonNode fetch(RestClient client, UUID channelId, Instant from, Instant to) {
        try {
            JsonNode body = client.get().uri(uri -> uri.path("/internal/analytics/sales").queryParam("channelId", channelId)
                    .queryParam("from", from.toString()).queryParam("to", to.toString()).build()).retrieve().body(JsonNode.class);
            return body == null ? tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : body;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw Http.unavailable();
        }
    }

    /** {@code amount} w groszach; {@code offerIds} to oferty w zamówieniu (jedna dla usługi). */
    public record Sale(Instant at, long amount, List<UUID> offerIds) {
    }
}
