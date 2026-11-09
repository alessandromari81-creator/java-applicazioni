/*
 * FILE: RisorsaNonTrovataException.java
 * A COSA SERVE: eccezione nostra, lanciata dai service quando un utente o un corso con un certo id
 * non esiste. Ha un nome e un significato precisi, così GestoreErrori sa che deve rispondere con
 * una pagina 404 (con NoSuchElementException, generica di Java, non potremmo esserne sicuri).
 * PARAGONE LARAVEL: è la ModelNotFoundException che lancia findOrFail().
 */
package it.palestra.exception;

// RuntimeException = eccezione "non controllata": non obbliga a scrivere try/catch o "throws"
// in ogni metodo che la attraversa (come le eccezioni normali di PHP).
public class RisorsaNonTrovataException extends RuntimeException {

    public RisorsaNonTrovataException(String messaggio) {
        super(messaggio); // il messaggio è leggibile poi con e.getMessage()
    }
}
