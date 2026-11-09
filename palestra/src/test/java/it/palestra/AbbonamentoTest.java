/*
 * FILE: AbbonamentoTest.java
 * A COSA SERVE: test UNITARI sui calcoli degli abbonamenti: data di fine, prezzo e stato. Sono test di Java
 * "puro": NON avviano Spring, né il database, né MockMvc, quindi sono velocissimi (millisecondi). Si usano per la
 * logica che dipende solo dai suoi dati di ingresso. Si danno date FISSE (LocalDate.of(...)), non "oggi", così il
 * risultato è sempre lo stesso, domani come tra un anno.
 * DIFFERENZA CON GLI ALTRI TEST: quelli (MockMvc) simulano una richiesta HTTP attraverso tutta l'applicazione;
 * questi chiamano direttamente un metodo e guardano il risultato. Come i test "Unit" rispetto ai "Feature" in Laravel.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.model.Abbonamento;
import it.palestra.model.StatoAbbonamento;
import it.palestra.model.TipoAbbonamento;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbbonamentoTest {

    // ----------------------------------------------------------- DATA DI FINE

    @Test
    @DisplayName("Mensile: dal 1 gennaio finisce il 31 gennaio")
    void mensileDalPrimoDelMese() {
        // "finisce il giorno prima della ricorrenza": 1 gennaio + 1 mese = 1 febbraio, meno un giorno = 31 gennaio.
        assertThat(TipoAbbonamento.MENSILE.calcolaFine(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("Mensile partito il 31 gennaio: febbraio è più corto, si ferma al suo ultimo giorno")
    void mensileDalTrentunoGennaio() {
        // 31 gennaio + 1 mese: il 31 febbraio non esiste, quindi si arriva al 28 febbraio (2026 non è bisestile);
        // poi si toglie un giorno. Documenta il comportamento: non è un errore, è la regola di plusMonths().
        assertThat(TipoAbbonamento.MENSILE.calcolaFine(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 27));
    }

    @Test
    @DisplayName("Trimestrale: dal 15 marzo finisce il 14 giugno")
    void trimestrale() {
        assertThat(TipoAbbonamento.TRIMESTRALE.calcolaFine(LocalDate.of(2026, 3, 15))).isEqualTo(LocalDate.of(2026, 6, 14));
    }

    @Test
    @DisplayName("Annuale: dal 1 gennaio finisce il 31 dicembre")
    void annuale() {
        assertThat(TipoAbbonamento.ANNUALE.calcolaFine(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Annuale partito il 29 febbraio (anno bisestile): l'anno dopo non c'è il 29, si ferma al 28")
    void annualeDaAnnoBisestile() {
        // 29/02/2028 + 12 mesi = 28/02/2029 (il 2029 non è bisestile); meno un giorno = 27/02/2029.
        assertThat(TipoAbbonamento.ANNUALE.calcolaFine(LocalDate.of(2028, 2, 29))).isEqualTo(LocalDate.of(2029, 2, 27));
    }

    // ------------------------------------------------------------------ PREZZO

    @Test
    @DisplayName("Prezzi di listino dei tre tipi")
    void prezziDiListino() {
        // isEqualByComparingTo confronta il VALORE dei BigDecimal (40 e 40.00 sono uguali per valore, ma equals()
        // di BigDecimal li considera diversi perché hanno una "scala" diversa: per i soldi si confronta così).
        assertThat(TipoAbbonamento.MENSILE.getPrezzoListino()).isEqualByComparingTo("40.00");
        assertThat(TipoAbbonamento.TRIMESTRALE.getPrezzoListino()).isEqualByComparingTo("105.00");
        assertThat(TipoAbbonamento.ANNUALE.getPrezzoListino()).isEqualByComparingTo("380.00");
    }

    @Test
    @DisplayName("Creando un abbonamento, fine e prezzo vengono calcolati dal tipo")
    void ilCostruttoreCalcolaFineEPrezzo() {
        // L'utente è null perché qui non serve (non c'è database): si prova solo il calcolo.
        Abbonamento a = new Abbonamento(null, TipoAbbonamento.TRIMESTRALE, LocalDate.of(2026, 3, 15));

        assertThat(a.getDataInizio()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(a.getDataFine()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(a.getPrezzo()).isEqualByComparingTo("105.00");
    }

    // -------------------------------------------------------------------- STATO

    @Test
    @DisplayName("Lo stato dipende dalla data: prima = futuro, dentro = attivo, dopo = scaduto (estremi inclusi)")
    void statoRispettoAllaData() {
        // Mensile dal 10 marzo 2026: dura dal 10 marzo al 9 aprile INCLUSI.
        Abbonamento a = new Abbonamento(null, TipoAbbonamento.MENSILE, LocalDate.of(2026, 3, 10));

        assertThat(a.stato(LocalDate.of(2026, 3, 9))).isEqualTo(StatoAbbonamento.FUTURO);   // il giorno PRIMA dell'inizio
        assertThat(a.stato(LocalDate.of(2026, 3, 10))).isEqualTo(StatoAbbonamento.ATTIVO);  // il giorno di inizio
        assertThat(a.stato(LocalDate.of(2026, 3, 25))).isEqualTo(StatoAbbonamento.ATTIVO);  // a metà
        assertThat(a.stato(LocalDate.of(2026, 4, 9))).isEqualTo(StatoAbbonamento.ATTIVO);   // l'ultimo giorno (incluso)
        assertThat(a.stato(LocalDate.of(2026, 4, 10))).isEqualTo(StatoAbbonamento.SCADUTO); // il giorno DOPO la fine
    }
}
