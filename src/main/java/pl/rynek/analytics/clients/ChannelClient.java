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

/** Kanał konta z channel-service: statystyki dotyczą kanału sprzedawcy. */
@Component
public class ChannelClient {

    private final RestClient http;

    public ChannelClient(@Value("${rynek.channel.url}") String url) {
        this.http = Http.client(url);
    }

    /** Kanał konta albo pusty, gdy konto go nie ma. */
    public Optional<UUID> channelOf(UUID userId) {
        try {
            JsonNode channel = http.get().uri("/internal/channels/by-owner/{userId}", userId).retrieve().body(JsonNode.class);
            return channel == null ? Optional.empty() : Optional.of(UUID.fromString(channel.path("id").asString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw Http.unavailable();
        }
    }
}
