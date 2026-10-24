package pl.rynek.analytics.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.analytics.api.dto.RecordEventsRequest;

import static pl.rynek.analytics.common.NotImplemented.notImplemented;

@RestController
public class AnalyticsController {

    /**
     * Wsadowy odbiór zdarzeń klienta (wyświetlenie oferty, odtworzenie filmu, klik „Kup teraz”). Dostęp: pub.
     */
    @PostMapping("/api/v1/analytics/events")
    public void recordEvents(@RequestBody RecordEventsRequest request) {
        throw notImplemented();
    }
}
