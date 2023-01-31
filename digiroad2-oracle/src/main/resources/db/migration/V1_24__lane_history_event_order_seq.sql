CREATE SEQUENCE lane_history_event_order_seq INCREMENT 1 MINVALUE 1 START 1;

ALTER TABLE lane_history ADD COLUMN event_order_number NUMERIC;