-- V9: indici per la ricerca di testo sugli utenti (parametro "q" delle liste, in pagine e API).
-- La ricerca cerca il testo OVUNQUE dentro nome, cognome o email, senza distinguere maiuscole (lower(colonna) LIKE lower('%testo%')). Un
-- indice normale non serve a questo tipo di ricerca (funziona solo per l'inizio della parola), quindi senza indici il database leggeva
-- TUTTA la tabella ad ogni ricerca: con 100.000 utenti circa 60 ms per il conteggio e altrettanti per la pagina.
-- Si usano indici "trigrammi" (estensione pg_trgm, GIN): spezzano il testo in gruppi di 3 lettere e trovano subito le righe che li
-- contengono. Misurato con 100.000 utenti: ricerca di un termine comune da circa 120 ms a 4 ms, di uno raro o inesistente sotto 2 ms.
-- Le espressioni devono essere IDENTICHE a quelle della query (lower(colonna)), altrimenti il database non usa l'indice.
-- Limiti noti: per un testo di meno di 3 lettere l'indice non aiuta e la ricerca resta una lettura completa (comunque corretta).
-- Non si indicizza corso.nome: i corsi sono poche decine.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_utente_nome_trgm ON utente USING gin (lower(nome) gin_trgm_ops);
CREATE INDEX idx_utente_cognome_trgm ON utente USING gin (lower(cognome) gin_trgm_ops);
CREATE INDEX idx_utente_email_trgm ON utente USING gin (lower(email) gin_trgm_ops);
