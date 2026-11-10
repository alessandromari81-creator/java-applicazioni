/*
 * FILE: TipoTokenPassword.java
 * A COSA SERVE: i due motivi per cui si può ricevere un link per scegliere la password:
 *   RESET        l'utente ha cliccato «Password dimenticata»;
 *   ATTIVAZIONE  l'amministratore ha invitato l'utente (creato nel pannello, senza password) a impostarla.
 * Cambiano soltanto la durata del link e il testo dell'email.
 */
package it.palestra.model;

public enum TipoTokenPassword {
    RESET,
    ATTIVAZIONE
}
