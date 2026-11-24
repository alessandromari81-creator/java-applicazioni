/*
 * FILE: DatiRegistrazione.java
 * A COSA SERVE: i dati di una registrazione libera così come li vuole RegistrazioneService: anagrafica, indirizzo e il consenso
 * facoltativo al marketing. È un record (oggetto di soli dati, immutabile) del livello dei service: non conosce il web. Arriva dal
 * modulo della pagina (RegistrazioneForm.comeDati()).
 * Restano fuori di proposito l'accettazione dell'informativa privacy (è obbligatoria e la controlla già il modulo prima di arrivare qui)
 * e il campo trappola "sito" (lo controlla il controller: chi lo compila è un programma, non una persona).
 */
package it.palestra.service;

public record DatiRegistrazione(String nome, String cognome, String email, String indirizzo, String cap, String citta,
                                boolean consensoMarketing) {
}
