package pl.rynek.analytics.clients;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Wspólne ustawienia klientów innych serwisów: krótkie limity czasu i jeden komunikat o niedostępności. */
final class Http {

    private Http() {
    }

    static RestClient client(String baseUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Statystyki są chwilowo niedostępne. Spróbuj ponownie za chwilę.");
    }
}
