/*
 * FILE: StatoAbbonamento.java
 * A COSA SERVE: in che situazione si trova un abbonamento rispetto a una certa data. NON è salvato nel database:
 * si calcola ogni volta confrontando le date (così non può mai diventare "vecchio" o incoerente: nessun
 * programma notturno deve aggiornare la colonna). Vedi Abbonamento.stato(oggi).
 */
package it.palestra.model;

public enum StatoAbbonamento {

    FUTURO("Non ancora iniziato"),   // la data di inizio è dopo oggi
    ATTIVO("Attivo"),                // oggi è compreso tra inizio e fine (estremi inclusi)
    SCADUTO("Scaduto");              // la data di fine è prima di oggi

    private final String etichetta;

    StatoAbbonamento(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() { return etichetta; }
}
