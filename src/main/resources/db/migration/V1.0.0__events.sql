CREATE SCHEMA IF NOT EXISTS kafka_demo_idempotent_consumer;

CREATE TABLE kafka_demo_idempotent_consumer.processed_event (
    eventid uuid NOT NULL,
    CONSTRAINT processed_event_ids_pkey PRIMARY KEY (eventid)
);

CREATE TABLE kafka_demo_idempotent_consumer.outbox_event (
                              id uuid NOT NULL,
                              destination varchar(255) NULL,
                              payload varchar(4096) NULL,
                              timestamp int8 NOT NULL,
                              version varchar(255) NULL,
                              CONSTRAINT outbox_pkey PRIMARY KEY (id)
);

