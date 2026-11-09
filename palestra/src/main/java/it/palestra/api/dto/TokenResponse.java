/*
 * FILE: TokenResponse.java
 * A COSA SERVE: la risposta di login e rinnovo, nel formato STANDARD OAuth2 (RFC 6749, lo stesso di Keycloak), così i client
 * e gli script che già conosci funzionano senza adattamenti:
 *   { "access_token": "eyJ...", "token_type": "Bearer", "expires_in": 3600,
 *     "refresh_token": "Xk3...", "refresh_expires_in": 604800 }
 *   - access_token        il JWT da mandare in ogni richiesta ("Authorization: Bearer ...");
 *   - token_type          sempre "Bearer";
 *   - expires_in          tra quanti SECONDI scade l'access token (la data assoluta è già nel JWT, campo "exp");
 *   - refresh_token       token per ottenerne uno nuovo senza rifare il login (POST /api/auth/refresh);
 *   - refresh_expires_in  tra quanti secondi scade il refresh token.
 * I nomi sono in snake_case perché così li vuole lo standard OAuth2; il resto della nostra API usa invece camelCase.
 * @JsonProperty dice a Jackson che nome usare nel JSON per ciascun campo Java.
 */
package it.palestra.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.palestra.service.SessioneService.Sessione;

public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_expires_in") long refreshExpiresIn) {

    // Costruisce la risposta dalla coppia di token appena emessa.
    public static TokenResponse da(Sessione s) {
        return new TokenResponse(s.accessToken(), "Bearer", s.accessScadeTraSecondi(),
                s.refreshToken(), s.refreshScadeTraSecondi());
    }
}
