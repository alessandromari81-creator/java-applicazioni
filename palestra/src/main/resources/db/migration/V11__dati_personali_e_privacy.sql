-- V11: dati personali aggiuntivi e consensi privacy degli utenti.
-- Tutte le colonne sono facoltative (NULL): gli utenti già esistenti, creati dall'amministratore, non le hanno. Le compila chi si
-- registra da solo (nel modulo di registrazione sono obbligatori indirizzo, CAP, città e l'accettazione dell'informativa).
--   indirizzo, cap, citta       indirizzo di casa (via e numero, CAP, città).
--   privacy_accettata_il        quando l'utente ha accettato l'informativa sulla privacy (NULL = non registrata, es. utente creato dall'admin).
--   privacy_versione            quale versione dell'informativa ha accettato (serve a provare a cosa ha acconsentito se il testo cambia).
--   consenso_marketing_il       quando ha dato il consenso FACOLTATIVO alle comunicazioni promozionali (NULL = nessun consenso).
ALTER TABLE utente ADD COLUMN indirizzo VARCHAR(255);
ALTER TABLE utente ADD COLUMN cap VARCHAR(10);
ALTER TABLE utente ADD COLUMN citta VARCHAR(100);
ALTER TABLE utente ADD COLUMN privacy_accettata_il TIMESTAMP WITH TIME ZONE;
ALTER TABLE utente ADD COLUMN privacy_versione VARCHAR(20);
ALTER TABLE utente ADD COLUMN consenso_marketing_il TIMESTAMP WITH TIME ZONE;
