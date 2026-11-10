/*
 * FILE: ValidatoreRevocaToken.java
 * A COSA SERVE: un controllo in più sui token JWT, fatto a ogni richiesta alla API DOPO firma e scadenza: il token è stato REVOCATO?
 * PROBLEMA CHE RISOLVE: un JWT è valido finché non scade (60 minuti), il server non lo "ricorda". Quindi, senza questo controllo, dopo un
 * logout, un cambio password o la rimozione del ruolo ADMIN, il vecchio token continuerebbe a funzionare fino alla scadenza.
 * COME FUNZIONA: ogni token contiene il claim "v" = versione dei token dell'utente al momento dell'emissione. Qui si legge dal database
 * la versione ATTUALE (colonna utente.versione_token) e si confronta:
 *   - utente inesistente (eliminato)  -> token rifiutato;
 *   - claim "v" assente (token emesso prima di questa funzione) -> rifiutato: basta rifare il refresh;
 *   - "v" diverso dalla versione attuale -> revocato, rifiutato.
 * Per revocare tutti i token di un utente basta aumentare la sua versione di 1 (vedi Utente.revocaTokenAccesso).
 * COSTO: una SELECT per indice unico (l'email) a ogni richiesta alla API. Se un giorno pesasse si può mettere una cache di pochi secondi,
 * al prezzo di ritardare la revoca di altrettanto.
 * Il MOTIVO del rifiuto finisce nel LOG dell'app ("WARN API ... rifiutata con 401 (...)"), mai nella risposta al client.
 */
package it.palestra.config;

import it.palestra.repository.UtenteRepository;
import it.palestra.service.JwtService;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class ValidatoreRevocaToken implements OAuth2TokenValidator<Jwt> {

    private final UtenteRepository utenti;

    public ValidatoreRevocaToken(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    // Chiamato da Spring Security per ogni token con firma e scadenza già valide. Restituisce "successo" oppure un errore con il motivo.
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String email = token.getSubject();
        Long versioneNelToken = token.getClaim(JwtService.CLAIM_VERSIONE);      // i numeri del JSON arrivano come Long
        if (email == null || versioneNelToken == null) {
            return rifiuta("il token non contiene la versione (emesso prima della revoca): rifai il refresh o il login");
        }
        Optional<Integer> versioneAttuale = utenti.trovaVersioneToken(email);
        if (versioneAttuale.isEmpty()) {
            return rifiuta("l'utente del token non esiste: eliminato o mai creato");
        }
        if (versioneAttuale.get().longValue() != versioneNelToken) {
            return rifiuta("token revocato (logout, cambio password o ruoli, o sospetto furto del refresh token)");
        }
        return OAuth2TokenValidatorResult.success();
    }

    // ATTENZIONE: Spring Security accetta nella descrizione di un errore OAuth2 solo caratteri ASCII "semplici" (regola RFC 6750: niente
    // lettere accentate, né virgolette doppie o barre rovesciate). Un motivo con una "ù" farebbe fallire la creazione dell'errore e il
    // log mostrerebbe solo "Invalid token". Per questo i motivi qui sotto sono scritti senza accenti.
    private OAuth2TokenValidatorResult rifiuta(String motivo) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("token_revocato", motivo, null));
    }
}
