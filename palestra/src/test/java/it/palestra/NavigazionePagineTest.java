/*
 * FILE: NavigazionePagineTest.java
 * A COSA SERVE: test UNITARI (senza Spring) dei controlli di navigazione delle liste HTML: numeri di pagina con i puntini,
 * "Risultati X–Y di N", link che conservano ricerca/dimensione/ordinamento, e intestazioni cliccabili con la freccia.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

class NavigazionePagineTest {

    // Costruisce la navigazione per una lista di "totale" elementi, mostrando la pagina "numero" (0 = prima).
    private NavigazionePagine nav(int totale, int numero, int size, String sort, String q) {
        RichiestaPagina r = RichiestaPagina.da(numero, size, sort, q, OrdinamentiListe.UTENTI);
        int da = numero * size;
        int quanti = Math.max(0, Math.min(size, totale - da));
        List<String> contenuto = Collections.nCopies(quanti, "x");
        Page<String> pagina = new PageImpl<>(contenuto, r.pageable(), totale);
        return new NavigazionePagine("/utenti", pagina, r);
    }

    // I numeri come testo: "1 … 9 *10* 11" (la pagina corrente tra asterischi, i puntini come "…").
    private String numeri(NavigazionePagine n) {
        return n.getNumeri().stream()
                .map(v -> v.puntini() ? "…" : (v.corrente() ? "*" + v.numeroVisibile() + "*" : String.valueOf(v.numeroVisibile())))
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    // ------------------------------------------------------------------------- NUMERI DI PAGINA

    @Test
    @DisplayName("Poche pagine: si mostrano tutte, senza puntini")
    void pochePagine() {
        assertThat(numeri(nav(100, 0, 20, null, null))).isEqualTo("*1* 2 3 4 5");
        assertThat(numeri(nav(100, 2, 20, null, null))).isEqualTo("1 2 *3* 4 5");
    }

    @Test
    @DisplayName("Tante pagine: prima, ultima e le vicine della corrente, con i puntini nei salti")
    void tantePagine() {
        // 20 pagine da 10 elementi = 200 elementi
        assertThat(numeri(nav(200, 0, 10, null, null))).isEqualTo("*1* 2 3 … 20");
        assertThat(numeri(nav(200, 9, 10, null, null))).isEqualTo("1 … 8 9 *10* 11 12 … 20");
        assertThat(numeri(nav(200, 19, 10, null, null))).isEqualTo("1 … 18 19 *20*");
    }

    @Test
    @DisplayName("Con una sola pagina (o nessun risultato) non servono i controlli di pagina")
    void unaSolaPagina() {
        assertThat(nav(5, 0, 20, null, null).isHaPagine()).isFalse();
        assertThat(nav(0, 0, 20, null, null).isHaPagine()).isFalse();
        assertThat(nav(0, 0, 20, null, null).getNumeri()).isEmpty();
        assertThat(nav(21, 0, 20, null, null).isHaPagine()).isTrue();
    }

    // ---------------------------------------------------------------------- "RISULTATI X–Y DI N"

    @Test
    @DisplayName("Da / a / totale: 'Risultati 21–40 di 57' e l'ultima pagina parziale")
    void daAETotale() {
        NavigazionePagine seconda = nav(57, 1, 20, null, null);
        assertThat(seconda.getDa()).isEqualTo(21);
        assertThat(seconda.getA()).isEqualTo(40);
        assertThat(seconda.getTotale()).isEqualTo(57);

        NavigazionePagine ultima = nav(57, 2, 20, null, null);
        assertThat(ultima.getDa()).isEqualTo(41);
        assertThat(ultima.getA()).isEqualTo(57);       // l'ultima pagina ha solo 17 elementi
    }

    @Test
    @DisplayName("Nessun risultato: da/a valgono 0 e la lista risulta vuota")
    void nessunRisultato() {
        NavigazionePagine n = nav(0, 0, 20, null, null);
        assertThat(n.isVuota()).isTrue();
        assertThat(n.getDa()).isZero();
        assertThat(n.getA()).isZero();
    }

    // ------------------------------------------------------------------------------------ LINK

    @Test
    @DisplayName("I link di pagina conservano ricerca, dimensione e ordinamento già scelti")
    void linkConservanoIlResto() {
        NavigazionePagine n = nav(200, 3, 10, "email,desc", "rossi");

        assertThat(n.urlPagina(4)).isEqualTo("/utenti?page=4&size=10&sort=email,desc&q=rossi");
        assertThat(n.getUrlPrecedente()).isEqualTo("/utenti?page=2&size=10&sort=email,desc&q=rossi");
        assertThat(n.getUrlSuccessiva()).isEqualTo("/utenti?page=4&size=10&sort=email,desc&q=rossi");
        assertThat(n.urlPagina(0)).isEqualTo("/utenti?size=10&sort=email,desc&q=rossi");    // la pagina 0 non si scrive
    }

    @Test
    @DisplayName("Precedente e successiva: assenti sulla prima e sull'ultima pagina")
    void precedenteESuccessiva() {
        assertThat(nav(100, 0, 20, null, null).isHaPrecedente()).isFalse();
        assertThat(nav(100, 0, 20, null, null).isHaSuccessiva()).isTrue();
        assertThat(nav(100, 4, 20, null, null).isHaSuccessiva()).isFalse();
        assertThat(nav(100, 4, 20, null, null).isHaPrecedente()).isTrue();
    }

    @Test
    @DisplayName("Azzerare la ricerca toglie 'q' e riparte dalla prima pagina, tenendo dimensione e ordinamento")
    void azzeraRicerca() {
        assertThat(nav(200, 3, 10, "email,desc", "rossi").getUrlAzzeraRicerca())
                .isEqualTo("/utenti?size=10&sort=email,desc");
    }

    // ----------------------------------------------------------------------- ORDINAMENTO

    @Test
    @DisplayName("Clic su una colonna: crescente; secondo clic sulla stessa: decrescente; sempre dalla prima pagina")
    void ordinamentoDalleIntestazioni() {
        NavigazionePagine base = nav(200, 3, 10, null, null);       // ordinata per cognome (predefinito) crescente

        assertThat(base.urlOrdina("email")).isEqualTo("/utenti?size=10&sort=email,asc");        // altra colonna: asc, pagina 0
        assertThat(base.urlOrdina("cognome")).isEqualTo("/utenti?size=10&sort=cognome,desc");   // stessa colonna: inverte

        NavigazionePagine giu = nav(200, 3, 10, "cognome,desc", null);
        assertThat(giu.urlOrdina("cognome")).isEqualTo("/utenti?size=10&sort=cognome,asc");     // e di nuovo: torna crescente
    }

    @Test
    @DisplayName("La freccia compare solo sulla colonna ordinata, ▲ crescente e ▼ decrescente")
    void freccia() {
        assertThat(nav(10, 0, 10, null, null).freccia("cognome")).isEqualTo("▲");
        assertThat(nav(10, 0, 10, "cognome,desc", null).freccia("cognome")).isEqualTo("▼");
        assertThat(nav(10, 0, 10, null, null).freccia("email")).isEmpty();
    }
}
