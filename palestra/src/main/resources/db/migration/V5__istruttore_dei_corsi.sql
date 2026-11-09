-- FILE: V5__istruttore_dei_corsi.sql
-- A COSA SERVE: quinta migration: collega ogni corso al suo ISTRUTTORE (un utente con ruolo ISTRUTTORE).
-- RELAZIONE UNO-A-MOLTI: un corso ha al massimo UN istruttore, ma un istruttore può tenere PIÙ corsi.
-- Si implementa con una colonna "istruttore_id" nella tabella corso (chiave esterna verso utente), come
-- $table->foreignId('istruttore_id')->nullable()->constrained('utenti') in una migration Laravel.
--
-- La colonna può essere NULL: un corso può esistere senza istruttore (non ancora assegnato).
-- Il database garantisce solo che l'id esista nella tabella utente. Le regole "deve avere il ruolo ISTRUTTORE" e
-- "non può essere iscritto al proprio corso" NON sono esprimibili con una semplice chiave esterna: le controlla
-- il codice dell'applicazione (CorsoService e UtenteService).
-- I corsi già presenti restano senza istruttore (NULL).

ALTER TABLE corso ADD COLUMN istruttore_id BIGINT REFERENCES utente (id);

-- Indice: rende veloci le ricerche "quali corsi tiene questo istruttore?" (usate per le regole sopra).
CREATE INDEX idx_corso_istruttore ON corso (istruttore_id);
