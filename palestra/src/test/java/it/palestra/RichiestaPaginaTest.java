/*
 * FILE: RichiestaPaginaTest.java
 * A COSA SERVE: test UNITARI (senza Spring) delle regole sui parametri delle liste: valori predefiniti, limiti di page e
 * size, ordinamenti ammessi, ricerca, e costruzione dei link. Sono veloci e provano la logica da sola.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.ParametriPaginaNonValidiException;
import it.palestra.pagina.RichiestaPagina;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class RichiestaPaginaTest {

    // Scorciatoia: costruisce una richiesta per la lista utenti.
    private RichiestaPagina utenti(Integer page, Integer size, String sort, String q) {
        return RichiestaPagina.da(page, size, sort, q, OrdinamentiListe.UTENTI);
    }

    // Estrae i messaggi d'errore di un parametro da una richiesta sbagliata.
    private ParametriPaginaNonValidiException errore(Integer page, Integer size, String sort, String q) {
        return (ParametriPaginaNonValidiException) catchThrowable(() -> utenti(page, size, sort, q));
    }

    private Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }

    // ------------------------------------------------------------------ VALORI PREDEFINITI

    @Test
    @DisplayName("Senza parametri: pagina 0, 20 elementi, ordinamento di partenza, nessuna ricerca")
    void valoriPredefiniti() {
        RichiestaPagina r = utenti(null, null, null, null);

        assertThat(r.page()).isZero();
        assertThat(r.size()).isEqualTo(20);
        assertThat(r.campo()).isEqualTo("cognome");
        assertThat(r.direzione()).isEqualTo(Sort.Direction.ASC);
        assertThat(r.q()).isNull();
        assertThat(r.sortEsplicito()).isNull();       // nessun ordinamento scelto dal client
    }

    @Test
    @DisplayName("Ordinamento di partenza degli abbonamenti: dalla fine più recente (decrescente)")
    void ordinamentoDiPartenzaAbbonamenti() {
        RichiestaPagina r = RichiestaPagina.da(null, null, null, null, OrdinamentiListe.ABBONAMENTI);

        assertThat(r.campo()).isEqualTo("dataFine");
        assertThat(r.direzione()).isEqualTo(Sort.Direction.DESC);
    }

    // --------------------------------------------------------------------------- PAGE E SIZE

    @Test
    @DisplayName("Page e size validi vengono accettati, compresi i limiti (page 0, size 1 e 100)")
    void valoriValidi() {
        assertThat(utenti(0, 1, null, null).size()).isEqualTo(1);
        assertThat(utenti(7, 100, null, null).page()).isEqualTo(7);
        assertThat(utenti(7, 100, null, null).pageable().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Page negativa e size fuori da 1..100 sono ERRORI (non vengono corretti in silenzio)")
    void pageESizeNonValidi() {
        assertThat(errore(-1, null, null, null).getCampi()).containsOnlyKeys("page");
        assertThat(errore(null, 0, null, null).getCampi()).containsOnlyKeys("size");
        assertThat(errore(null, 101, null, null).getCampi()).containsOnlyKeys("size");
        assertThat(errore(null, -5, null, null).getCampi().get("size").get(0)).contains("1 e 100");
    }

    @Test
    @DisplayName("Più parametri sbagliati: gli errori sono segnalati tutti insieme")
    void erroriMultipli() {
        assertThat(errore(-1, 500, "boh", null).getCampi()).containsOnlyKeys("page", "size", "sort");
    }

    // ------------------------------------------------------------------------------ SORT

    @Test
    @DisplayName("Sort: campo da solo = crescente; con direzione asc/desc (maiuscole e spazi tollerati)")
    void sortValidi() {
        assertThat(utenti(null, null, "email", null).direzione()).isEqualTo(Sort.Direction.ASC);
        assertThat(utenti(null, null, "email,desc", null).direzione()).isEqualTo(Sort.Direction.DESC);
        assertThat(utenti(null, null, " nome , DESC ", null).campo()).isEqualTo("nome");
        assertThat(utenti(null, null, "nome,desc", null).sortEsplicito()).isEqualTo("nome,desc");
    }

    @Test
    @DisplayName("Sort con un campo NON ammesso (es. passwordHash) o con direzione sbagliata: errore")
    void sortNonValidi() {
        assertThat(errore(null, null, "passwordHash", null).getCampi().get("sort").get(0))
                .contains("Campi ammessi").contains("cognome").contains("email");
        assertThat(errore(null, null, "cognome,su", null).getCampi()).containsOnlyKeys("sort");
        assertThat(errore(null, null, "cognome,asc,x", null).getCampi()).containsOnlyKeys("sort");
        assertThat(errore(null, null, "cognome,", null).getCampi()).containsOnlyKeys("sort");
        assertThat(errore(null, null, "COGNOME", null).getCampi()).containsOnlyKeys("sort");   // i nomi sono esatti
    }

    @Test
    @DisplayName("L'ordinamento per il database ha sempre lo spareggio sull'id (pagine stabili)")
    void spareggioSuId() {
        Sort sort = utenti(null, null, "cognome,desc", null).pageable().getSort();

        assertThat(sort.stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("cognome:DESC", "nome:DESC", "id:DESC");
    }

    // ------------------------------------------------------------------------------- RICERCA

    @Test
    @DisplayName("La ricerca viene ripulita dagli spazi; vuota o solo spazi = nessuna ricerca; oltre 100 caratteri = errore")
    void ricerca() {
        assertThat(utenti(null, null, null, "  rossi  ").q()).isEqualTo("rossi");
        assertThat(utenti(null, null, null, "   ").q()).isNull();
        assertThat(utenti(null, null, null, "").q()).isNull();
        assertThat(utenti(null, null, null, "x".repeat(100)).q()).hasSize(100);
        assertThat(errore(null, null, null, "x".repeat(101)).getCampi()).containsOnlyKeys("q");
    }

    // -------------------------------------------------------------------------------- LINK

    @Test
    @DisplayName("I link riportano solo i valori NON predefiniti")
    void queryConSoloIValoriNonPredefiniti() {
        assertThat(utenti(null, null, null, null).query(0, null)).isEmpty();
        assertThat(utenti(null, 50, "email,desc", "rossi").query(2, "email,desc"))
                .isEqualTo("?page=2&size=50&sort=email,desc&q=rossi");
    }

    @Test
    @DisplayName("Nel link il testo cercato è codificato: spazi, & e accenti non possono rompere o manipolare l'indirizzo")
    void ricercaCodificataNelLink() {
        String query = utenti(null, null, null, "a b&c=d è").query(0, null);

        assertThat(query).startsWith("?q=");
        assertThat(query).doesNotContain(" ").doesNotContain("&c=").doesNotContain("è");
        assertThat(query).contains("%26");                  // la & è stata codificata
    }
}
