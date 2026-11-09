/*
 * FILE: LoginRequest.java
 * A COSA SERVE: il JSON che si invia per fare login alla API: { "email": "...", "password": "..." }.
 * "Request" = dati in ingresso. Le annotazioni sono le stesse regole di validazione dei form.
 */
package it.palestra.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "L'email è obbligatoria") String email,
        @NotBlank(message = "La password è obbligatoria") String password) {
}
