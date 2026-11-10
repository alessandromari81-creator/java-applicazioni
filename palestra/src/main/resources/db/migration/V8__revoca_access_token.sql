-- V8: revoca degli access token (JWT) prima della scadenza.
-- Ogni access token porta dentro il numero di "versione" che l'utente aveva quando è stato emesso (claim "v"). A ogni richiesta alla API
-- si confronta quel numero con questa colonna: se sono diversi il token è stato revocato e viene rifiutato (401).
-- Per revocare TUTTI i token di accesso di un utente basta aumentare il numero di 1. Si fa al logout, quando si sospetta un furto del
-- refresh token, quando cambia la password o i ruoli. (PARAGONE LARAVEL: è come cambiare il "remember_token", ma per i token API.)
-- DEFAULT 0: gli utenti già esistenti partono da 0.
ALTER TABLE utente ADD COLUMN versione_token INTEGER NOT NULL DEFAULT 0;
