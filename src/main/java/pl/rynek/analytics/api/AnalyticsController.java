package pl.rynek.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.analytics.config.Viewer;
import pl.rynek.analytics.events.EventService;
import pl.rynek.analytics.events.EventService.EventInput;
import pl.rynek.analytics.events.EventService.EventType;

@RestController
public class AnalyticsController {

    private final EventService events;
    private final Viewer viewer;

    public AnalyticsController(EventService events, Viewer viewer) {
        this.events = events;
        this.viewer = viewer;
    }

    /**
     * Wsadowy odbiór zdarzeń klienta (oferta na liście, otwarta karta, film, dodanie do koszyka, „Kup teraz”), najwyżej 50 w żądaniu.
     * Zdarzenia o nieistniejących ofertach są pomijane. Dostęp: pub.
     */
    @PostMapping("/api/v1/analytics/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordEvents(HttpServletRequest request, @Valid @RequestBody RecordEventsRequest body) {
        events.record(viewer.of(request), body.events().stream().map(e -> new EventInput(e.type(), e.offerId(), e.at())).toList());
    }

    public record ClientEventRequest(@NotNull(message = "Podaj rodzaj zdarzenia.") EventType type, UUID offerId, Instant at) {
    }

    public record RecordEventsRequest(
            @NotNull(message = "Podaj listę zdarzeń.")
            @Size(max = 50, message = "Najwyżej 50 zdarzeń w jednym żądaniu.")
            List<@Valid @NotNull ClientEventRequest> events) {
    }
}
