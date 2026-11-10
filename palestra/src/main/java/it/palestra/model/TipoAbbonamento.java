/*
 * FILE: TipoAbbonamento.java
 * A COSA SERVE: i tipi di abbonamento venduti dalla palestra, ognuno con la sua durata in mesi e il suo prezzo
 * di LISTINO. È un enum Java che oltre ai valori contiene dati e un piccolo calcolo.
 * Per cambiare i prezzi di listino si modificano i numeri qui sotto (vale per i NUOVI abbonamenti: quelli già
 * creati conservano il prezzo con cui sono stati venduti).
 */
package it.palestra.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public enum TipoAbbonamento {

    // nome, etichetta da mostrare, durata in mesi, prezzo di listino in euro.
    // new BigDecimal("40.00") scritto come TESTO: dal numero 40.0 (double) potrebbero entrare errori di arrotondamento.
    MENSILE("Mensile", 1, new BigDecimal("40.00")),
    TRIMESTRALE("Trimestrale", 3, new BigDecimal("105.00")),
    ANNUALE("Annuale", 12, new BigDecimal("380.00"));

    private final String etichetta;
    private final int mesi;
    private final BigDecimal prezzoListino;

    // Costruttore dell'enum: viene chiamato una volta per ciascun valore elencato sopra.
    TipoAbbonamento(String etichetta, int mesi, BigDecimal prezzoListino) {
        this.etichetta = etichetta;
        this.mesi = mesi;
        this.prezzoListino = prezzoListino;
    }

    public String getEtichetta() { return etichetta; }
    public int getMesi() { return mesi; }
    public BigDecimal getPrezzoListino() { return prezzoListino; }

    // Ultimo giorno (INCLUSO) di un abbonamento che parte il giorno "inizio".
    // REGOLA: dura "mesi" mesi di calendario, e finisce il giorno prima della ricorrenza.
    //   es. mensile dal 1 gennaio -> finisce il 31 gennaio; trimestrale dal 15 marzo -> finisce il 14 giugno.
    // plusMonths() gestisce da solo i mesi di lunghezza diversa: se il giorno non esiste nel mese di arrivo si
    // ferma all'ultimo giorno di quel mese (es. 31 gennaio + 1 mese = 28 febbraio). Poi si toglie un giorno.
    public LocalDate calcolaFine(LocalDate inizio) {
        return inizio.plusMonths(mesi).minusDays(1);
    }
}
