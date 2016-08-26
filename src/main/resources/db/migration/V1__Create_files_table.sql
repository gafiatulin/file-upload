CREATE TABLE files ("id" bigserial NOT NULL, "name" varchar NOT NULL UNIQUE, "url" varchar, "media" varchar, "extra" text, "available" boolean, PRIMARY KEY ("id"));
CREATE TABLE IF NOT EXISTS public.journal ("ordering" bigserial, "persistence_id" varchar(255) NOT NULL, "sequence_number" bigint NOT NULL, "deleted" boolean DEFAULT FALSE, "tags" varchar(255) DEFAULT NULL, "message" bytea NOT NULL, PRIMARY KEY(ordering, persistence_id, sequence_number));
CREATE INDEX IF NOT EXISTS journal_persistence_id_sequence_number_idx ON public.journal(persistence_id, sequence_number);
CREATE TABLE IF NOT EXISTS public.snapshot ("persistence_id" varchar(255) NOT NULL, "sequence_number" bigint NOT NULL, "created" bigint NOT NULL, "snapshot" bytea NOT NULL, PRIMARY KEY(persistence_id, sequence_number));
