/*
 * FILE: NomeCognome.java
 * A COSA SERVE: "proiezione" di Spring Data: una query può restituire solo questi due campi invece dell'intero Utente. Si usa per mostrare
 * all'istruttore gli iscritti ai suoi corsi senza esporre altri dati (email, abbonamenti...): i dati non richiesti non vengono nemmeno letti.
 */
package it.palestra.repository;

public interface NomeCognome {
    String getNome();
    String getCognome();
}
