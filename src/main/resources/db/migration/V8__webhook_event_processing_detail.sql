-- =============================================================
-- V8: Enrich webhook_events with processing outcome detail
--
-- Adds columns the frontend can show without parsing the raw
-- payload: which resource was affected, a human-readable summary
-- of the action taken, and how long processing took.
-- =============================================================

ALTER TABLE webhook_events
    ADD COLUMN processed_resource_type    VARCHAR(40),
    ADD COLUMN processed_resource_id      BIGINT,
    ADD COLUMN processed_asaas_id         VARCHAR(255),
    ADD COLUMN processing_summary         TEXT,
    ADD COLUMN processing_duration_ms     BIGINT;

CREATE INDEX idx_webhook_events_resource
    ON webhook_events (processed_resource_type, processed_resource_id);
