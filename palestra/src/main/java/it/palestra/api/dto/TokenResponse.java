/*
 * FILE: TokenResponse.java
 * A COSA SERVE: la risposta al login: il token e quanto dura. Il client lo manda poi in ogni richiesta con
 * l'intestazione "Authorization: Bearer <token>".
 *   { "token": "eyJ...", "tipo": "Bearer", "scadeTraSecondi": 3600 }
 */
package it.palestra.api.dto;

public record TokenResponse(String token, String tipo, long scadeTraSecondi) {

    public static TokenResponse bearer(String token, long scadeTraSecondi) {
        return new TokenResponse(token, "Bearer", scadeTraSecondi);
    }
}
