/*
 * FILE: RegolaViolataException.java
 * A COSA SERVE: eccezione lanciata dai service quando un'operazione viene RIFIUTATA da una regola di business
 * (es. "non si elimina l'ultimo amministratore", "il corso ha degli iscritti", "l'abbonamento è già iniziato").
 * Chi la riceve ne mostra il messaggio:
 *   - le pagine HTML lo mostrano in un riquadro rosso (messaggio "errore" del flash);
 *   - la API REST risponde 409 Conflict con il messaggio in JSON.
 * PERCHÉ NON IllegalStateException: quella è generica e la lanciano anche le librerie di Java per veri errori di
 * programmazione. Se la API trasformasse ogni IllegalStateException in un 409, nasconderebbe dei bug dietro un
 * "conflitto". Con un'eccezione NOSTRA si sa con certezza che è una regola violata, non un malfunzionamento.
 * PARAGONE LARAVEL: un'eccezione personalizzata che poi Handler.php trasforma in una risposta 409.
 */
package it.palestra.exception;

public class RegolaViolataException extends RuntimeException {

    public RegolaViolataException(String messaggio) {
        super(messaggio);
    }
}
