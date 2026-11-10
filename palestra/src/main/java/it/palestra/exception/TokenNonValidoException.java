/*
 * FILE: TokenNonValidoException.java
 * A COSA SERVE: segnala che il link ricevuto per email (conferma registrazione, impostazione o reimpostazione della password) non si può usare:
 * non esiste, è scaduto o è già stato usato. Non distingue i tre casi di proposito: chi tenta di indovinare i link non deve sapere nulla.
 * Le pagine la trasformano in un messaggio «link non valido o scaduto» con il collegamento per richiederne uno nuovo.
 */
package it.palestra.exception;

public class TokenNonValidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TokenNonValidoException() {
        super("Link non valido o scaduto");
    }
}
