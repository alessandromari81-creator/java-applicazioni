/*
 * FILE: RefreshTokenNonValidoException.java
 * A COSA SERVE: lanciata quando un refresh token viene rifiutato (sconosciuto, scaduto, già usato/revocato, utente non più
 * abilitato). Estende AuthenticationException, così GestoreErroriApi la trasforma in una risposta 401. Il messaggio per il
 * client è sempre lo stesso; il motivo preciso si scrive solo nel log del server.
 */
package it.palestra.exception;

import org.springframework.security.core.AuthenticationException;

public class RefreshTokenNonValidoException extends AuthenticationException {

    public RefreshTokenNonValidoException(String motivo) {
        super(motivo);   // il motivo serve solo al log, non finisce nella risposta
    }
}
