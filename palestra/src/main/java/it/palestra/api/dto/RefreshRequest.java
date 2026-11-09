/*
 * FILE: RefreshRequest.java
 * A COSA SERVE: il JSON per rinnovare l'accesso o fare logout: { "refresh_token": "Xk3..." }. Il nome del campo è quello
 * standard OAuth2 (snake_case), come nella risposta del login.
 * Il campo obbligatorio NON si controlla con @NotBlank: l'errore di @Valid userebbe il nome Java ("refreshToken") mentre il
 * client ha scritto "refresh_token". Lo controlla AuthApiController, segnalando il nome che il client conosce.
 */
package it.palestra.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {
}
