package pl.rynek.analytics.clients;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Kanał i tytuł oferty z catalog-service ({@code /internal/offers/{id}/channel}), także dla szkiców. */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${rynek.catalog.url}") String url) {
        this.http = Http.client(url);
    }

    /** Oferta albo pusta, gdy jej nie ma; niedostępny catalog to wyjątek 503. */
    public Optional<OfferRef> offer(UUID offerId) {
        try {
            JsonNode body = http.get().uri("/internal/offers/{offerId}/channel", offerId).retrieve().body(JsonNode.class);
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(new OfferRef(UUID.fromString(body.path("channelId").asString()), body.path("title").asString("Oferta")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw Http.unavailable();
        }
    }

    public record OfferRef(UUID channelId, String title) {
    }
}
