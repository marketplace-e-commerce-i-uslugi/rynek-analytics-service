package pl.rynek.analytics.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.rynek.analytics.TestcontainersConfig;
import pl.rynek.analytics.clients.CatalogClient;
import pl.rynek.analytics.clients.CatalogClient.OfferRef;
import pl.rynek.analytics.clients.ChannelClient;
import pl.rynek.analytics.clients.SalesClient;
import pl.rynek.analytics.clients.SalesClient.Sale;

/** Zdarzenia i statystyki na prawdziwym PostgreSQL; channel, catalog i sprzedaż (order, booking) to atrapy. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AnalyticsFlowTests {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ChannelClient channels;
    @MockitoBean CatalogClient catalog;
    @MockitoBean SalesClient sales;

    private final Map<UUID, OfferRef> offers = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> noChannel = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(channels.channelOf(any())).willAnswer(call -> {
            UUID user = call.getArgument(0);
            return noChannel.contains(user) ? Optional.empty() : Optional.of(channelOf(user));
        });
        given(catalog.offer(any())).willAnswer(call -> Optional.ofNullable(offers.get((UUID) call.getArgument(0))));
        given(sales.sales(any(), any(), any())).willReturn(List.of());
    }

    @Test
    void odbiorZdarzen_zapisujeZnaneOfertyIPomijaReszte() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID offer = offer(seller, "Słuchawki");
        UUID unknown = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String body = "{\"events\":[{\"type\":\"OFFER_IMPRESSION\",\"offerId\":\"" + offer + "\",\"at\":\"" + Instant.now() + "\"},"
                + "{\"type\":\"OFFER_VIEW\",\"offerId\":\"" + offer + "\"},"
                + "{\"type\":\"VIDEO_PLAY\",\"offerId\":\"" + unknown + "\"},"
                + "{\"type\":\"BUY_NOW_CLICK\"},"
                + "{\"type\":\"ADD_TO_CART\",\"offerId\":\"" + offer + "\",\"at\":\"2001-01-01T00:00:00Z\"}]}";
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/analytics/events").header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"OFFER_VIEW\",\"offerId\":\"" + offer + "\"}]}")).andExpect(status().isNoContent());
        // Zapisane: 3 zdarzenia gościa (bez nieznanej oferty i bez offerId) i 1 zalogowanego; stary czas klienta zastąpiony czasem serwera.
        assertThat(count("select count(*) from analytics_events where offer_id = ?", offer)).isEqualTo(4);
        assertThat(count("select count(*) from analytics_events where offer_id = ? and user_id = ?", offer, user)).isEqualTo(1);
        assertThat(count("select count(*) from analytics_events where offer_id = ? and occurred_at < now() - interval '2 days'", offer)).isZero();
        assertThat(count("select count(*) from analytics_events where channel_id = ?", channelOf(seller))).isEqualTo(4);
        assertThat(count("select count(*) from analytics_events where offer_id = ?", unknown)).isZero();
    }

    @Test
    void odbiorZdarzen_walidacja() throws Exception {
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[{\"type\":\"LAJK\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[{}]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}")).andExpect(status().isNoContent());
        StringBuilder many = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            many.append(i == 0 ? "" : ",").append("{\"type\":\"OFFER_VIEW\"}");
        }
        mvc.perform(post("/api/v1/analytics/events").contentType(MediaType.APPLICATION_JSON).content(many + "]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Najwyżej 50 zdarzeń w jednym żądaniu.")));
    }

    @Test
    void panelWymagaLogowaniaIKanalu() throws Exception {
        mvc.perform(get("/api/v1/seller/analytics/overview")).andExpect(status().isUnauthorized());
        UUID bezKanalu = UUID.randomUUID();
        noChannel.add(bezKanalu);
        mvc.perform(get("/api/v1/seller/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(bezKanalu))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("Najpierw załóż kanał.")));
    }

    @Test
    void przegladLiczyOkresIPoprzedniOkres_tylkoDlaKanaluSprzedawcy() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID offer = offer(seller, "Słuchawki");
        UUID foreign = offer(other, "Cudza");
        // Bieżące 7 dni: 3 wyświetlenia i 1 film; poprzednie 7 dni: 1 wyświetlenie; cudzy kanał nie wchodzi.
        event("OFFER_IMPRESSION", offer, 0);
        event("OFFER_IMPRESSION", offer, 1);
        event("OFFER_IMPRESSION", offer, 6);
        event("OFFER_IMPRESSION", offer, 8);
        event("OFFER_IMPRESSION", offer, 20);
        event("VIDEO_PLAY", offer, 2);
        event("OFFER_IMPRESSION", foreign, 0);
        given(sales.sales(any(), any(), any())).willReturn(List.of(
                new Sale(Instant.now().minusSeconds(60), 12_345, List.of(offer)),
                new Sale(Instant.now().minusSeconds(120), 5_000, List.of(offer)),
                new Sale(Instant.now().minusSeconds(86400L * 9), 2_000, List.of(offer))));

        mvc.perform(get("/api/v1/seller/analytics/overview").param("range", "7d").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.range", is("7d")))
                .andExpect(jsonPath("$.kpis[*].key", contains("views", "videoViews", "orders", "revenue")))
                .andExpect(jsonPath("$.kpis[0].value", is(3))).andExpect(jsonPath("$.kpis[0].change", is(200)))
                .andExpect(jsonPath("$.kpis[1].value", is(1))).andExpect(jsonPath("$.kpis[1].change", is(100)))
                .andExpect(jsonPath("$.kpis[2].value", is(2))).andExpect(jsonPath("$.kpis[2].change", is(100)))
                .andExpect(jsonPath("$.kpis[3].value", is(173))).andExpect(jsonPath("$.kpis[3].money.amount", is(17_345)))
                .andExpect(jsonPath("$.kpis[3].money.currency", is("PLN"))).andExpect(jsonPath("$.kpis[3].change", is(767)));
        // Domyślnie 30 dni: bierze też zdarzenie sprzed 20 dni; poprzednie 30 dni są puste.
        mvc.perform(get("/api/v1/seller/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.range", is("30d"))).andExpect(jsonPath("$.kpis[0].value", is(5)));
        mvc.perform(get("/api/v1/seller/analytics/overview").param("range", "rok").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Nieznany okres. Wybierz 7d, 30d albo quarter.")));
    }

    @Test
    void wykresMaSlupekNaDzienDlaKwartaluNaTydzien_aPrzychodJestWZlotych() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID offer = offer(seller, "Słuchawki");
        event("OFFER_IMPRESSION", offer, 0);
        event("OFFER_IMPRESSION", offer, 0);
        event("OFFER_IMPRESSION", offer, 3);
        event("OFFER_IMPRESSION", offer, 7);
        String auth = bearer(seller);

        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "views").param("range", "7d").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].current", is(2))).andExpect(jsonPath("$[3].current", is(1))).andExpect(jsonPath("$[6].previous", is(1)))
                .andExpect(jsonPath("$[6].label", is(LocalDate.now(ZONE).getDayOfMonth() + "." + String.format("%02d", LocalDate.now(ZONE).getMonthValue()))));
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "views").param("range", "30d").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$", hasSize(30)));
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "views").param("range", "quarter").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$", hasSize(13))).andExpect(jsonPath("$[12].current", is(3))).andExpect(jsonPath("$[11].current", is(1)));
        given(sales.sales(any(), any(), any())).willReturn(List.of(new Sale(Instant.now().minusSeconds(60), 12_345, List.of(offer))));
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "revenue").param("range", "7d").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[6].current", is(123)));
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "orders").param("range", "7d").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[6].current", is(1)));
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "lajki").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "views").param("range", "x").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void najlepszeOfertyWgWyswietlen() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID first = offer(seller, "Wiertarka");
        UUID second = offer(seller, "Słuchawki");
        event("OFFER_IMPRESSION", first, 0);
        event("OFFER_IMPRESSION", second, 1);
        event("OFFER_IMPRESSION", second, 2);
        event("OFFER_VIEW", first, 0);
        event("OFFER_IMPRESSION", second, 40);
        mvc.perform(get("/api/v1/seller/analytics/top-offers").param("range", "7d").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].title", contains("Słuchawki", "Wiertarka")))
                .andExpect(jsonPath("$[0].views", is(2))).andExpect(jsonPath("$[0].offerId", is(second.toString())));
        mvc.perform(get("/api/v1/seller/analytics/top-offers").param("range", "quarter").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$[0].views", is(3)));
    }

    @Test
    void lejekOfertyZliczaEtapyISprzedaz_cudzaOfertaTo404() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID offer = offer(seller, "Słuchawki");
        UUID foreign = offer(other, "Cudza");
        for (int i = 0; i < 5; i++) {
            event("OFFER_IMPRESSION", offer, i);
        }
        event("OFFER_VIEW", offer, 0);
        event("OFFER_VIEW", offer, 1);
        event("ADD_TO_CART", offer, 0);
        event("BUY_NOW_CLICK", offer, 1);
        event("OFFER_VIEW", offer, 200);
        given(sales.sales(any(), any(), any())).willReturn(List.of(
                new Sale(Instant.now().minusSeconds(60), 100, List.of(offer)), new Sale(Instant.now().minusSeconds(60), 100, List.of(UUID.randomUUID()))));

        mvc.perform(get("/api/v1/seller/analytics/offers/" + offer).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title", is("Słuchawki"))).andExpect(jsonPath("$.views", is(5)))
                .andExpect(jsonPath("$.detailViews", is(2))).andExpect(jsonPath("$.addedToCart", is(2))).andExpect(jsonPath("$.purchases", is(1)));
        mvc.perform(get("/api/v1/seller/analytics/offers/" + foreign).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/analytics/offers/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/analytics/offers/nie-uuid").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNotFound());
    }

    @Test
    void niedzialajacySerwisSprzedazyDaje503_aWyswietleniaDzialajaBezNiego() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID offer = offer(seller, "Słuchawki");
        event("OFFER_IMPRESSION", offer, 0);
        given(sales.sales(any(), any(), any())).willThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Statystyki są chwilowo niedostępne. Spróbuj ponownie za chwilę."));
        mvc.perform(get("/api/v1/seller/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/seller/analytics/timeseries").param("metric", "views").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/seller/analytics/top-offers").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isOk());
    }

    // --- pomocnicze ---

    private UUID offer(UUID seller, String title) {
        UUID id = UUID.randomUUID();
        offers.put(id, new OfferRef(channelOf(seller), title));
        return id;
    }

    /** Zdarzenie sprzed {@code daysAgo} dni (dziś: minutę temu, wcześniej: południe tamtego dnia). */
    private void event(String type, UUID offer, int daysAgo) {
        Instant at = daysAgo == 0 ? Instant.now().minusSeconds(60)
                : LocalDate.now(ZONE).minusDays(daysAgo).atTime(12, 0).atZone(ZONE).toInstant();
        jdbc.update("insert into analytics_events (id, type, offer_id, channel_id, occurred_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), type, offer, offers.get(offer).channelId(), Timestamp.from(at));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static org.assertj.core.api.AbstractLongAssert<?> assertThat(long value) {
        return org.assertj.core.api.Assertions.assertThat(value);
    }

    private static UUID channelOf(UUID owner) {
        return UUID.nameUUIDFromBytes(("kanal-" + owner).getBytes());
    }

    private static String bearer(UUID userId) {
        return "Bearer user-" + userId;
    }
}
