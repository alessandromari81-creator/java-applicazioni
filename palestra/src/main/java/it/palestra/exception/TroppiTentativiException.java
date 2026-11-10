/*
 * FILE: TroppiTentativiException.java
 * A COSA SERVE: segnala che da un certo indirizzo (e per una certa email) ci sono stati troppi tentativi di login falliti e che
 * per un po' non se ne accettano altri. La API la trasforma in una risposta 429 "Too Many Requests" con l'intestazione Retry-After
 * (vedi GestoreErroriApi); le pagine rimandano al login con un avviso (vedi FiltroLimiteLogin).
 */
package it.palestra.exception;

public class TroppiTentativiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    // Tra quanti secondi si potrà riprovare (serve all'intestazione Retry-After).
    private final long secondiAttesa;

    public TroppiTentativiException(long secondiAttesa) {
        super("Troppi tentativi di accesso: riprova tra " + secondiAttesa + " secondi");
        this.secondiAttesa = secondiAttesa;
    }

    public long getSecondiAttesa() {
        return secondiAttesa;
    }
}
