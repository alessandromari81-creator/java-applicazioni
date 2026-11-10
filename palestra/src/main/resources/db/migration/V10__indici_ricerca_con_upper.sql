-- V10: corregge gli indici di ricerca della V9.
-- La V9 li aveva creati su lower(colonna), ma Spring Data (le ricerche "ContainingIgnoreCase") scrive la query con
-- upper(colonna) LIKE upper('%testo%'): un indice su un'espressione viene usato SOLO se la query usa la stessa identica espressione,
-- quindi gli indici della V9 non venivano mai usati (misurato: la ricerca restava a 70-140 ms con 100.000 utenti).
-- Qui si eliminano quegli indici inutili (rallentavano solo le scritture) e si creano gli equivalenti su upper(colonna).
-- Se la ricerca nel codice cambiasse (es. lower invece di upper) gli indici andrebbero rifatti: lo segnala il test
-- RicercaSqlTest, che controlla il testo SQL generato.
DROP INDEX IF EXISTS idx_utente_nome_trgm;
DROP INDEX IF EXISTS idx_utente_cognome_trgm;
DROP INDEX IF EXISTS idx_utente_email_trgm;

CREATE INDEX idx_utente_nome_trgm_upper ON utente USING gin (upper(nome) gin_trgm_ops);
CREATE INDEX idx_utente_cognome_trgm_upper ON utente USING gin (upper(cognome) gin_trgm_ops);
CREATE INDEX idx_utente_email_trgm_upper ON utente USING gin (upper(email) gin_trgm_ops);
