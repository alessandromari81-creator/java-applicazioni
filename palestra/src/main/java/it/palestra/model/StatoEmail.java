/*
 * FILE: StatoEmail.java
 * A COSA SERVE: gli stati in cui si può trovare una riga della coda delle email (tabella email_in_coda). Salvato come testo nel database.
 * Una email spedita non ha uno stato: la sua riga viene cancellata.
 */
package it.palestra.model;

public enum StatoEmail {

    // Aspetta il suo turno: sarà spedita al primo giro utile dopo prossimo_tentativo.
    DA_INVIARE,

    // Qualcuno (un giro del compito programmato, anche di un'altra istanza dell'app) l'ha presa e la sta spedendo.
    IN_CORSO,

    // I tentativi sono finiti senza successo: non si riprova più. La riga resta qualche ora (per capire cosa è successo) e poi la pulizia notturna la cancella.
    FALLITA
}
