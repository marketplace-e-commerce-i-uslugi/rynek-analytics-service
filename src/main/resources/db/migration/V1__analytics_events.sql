-- Zdarzenia klienta (POST /analytics/events), przypisane do kanału sprzedawcy oferty w chwili zapisu. user_id puste dla gościa.
-- Typy: OFFER_IMPRESSION (oferta pojawiła się na liście), OFFER_VIEW (otwarta karta), VIDEO_PLAY (film pokazany),
-- ADD_TO_CART, BUY_NOW_CLICK.
CREATE TABLE analytics_events (
    id          uuid PRIMARY KEY,
    type        varchar(20) NOT NULL,
    offer_id    uuid        NOT NULL,
    channel_id  uuid        NOT NULL,
    user_id     uuid,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX analytics_events_channel_idx ON analytics_events (channel_id, type, occurred_at);
CREATE INDEX analytics_events_offer_idx ON analytics_events (offer_id, type, occurred_at);
