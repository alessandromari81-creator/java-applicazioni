/*
 * FILE: CacheRuoliTest.java
 * A COSA SERVE: verifica la cache dell'elenco dei ruoli (UtenteService.tuttiIRuoli, @Cacheable("ruoli")) MISURANDO, con le
 * statistiche di Hibernate, quante query arrivano davvero al database e, con quelle di Caffeine, quante letture sono
 * "hit" (trovate in cache) o "miss" (non trovate). Prova anche che la cache non cambia il risultato visibile.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Ruolo;
import it.palestra.service.UtenteService;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.caffeine.CaffeineCache;

class CacheRuoliTest extends BaseWebTest {

    @Autowired UtenteService service;
    @Autowired EntityManagerFactory emf;

    private Statistics statistiche() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    // Le statistiche interne di Caffeine (richiede "recordStats" in spring.cache.caffeine.spec).
    private com.github.benmanes.caffeine.cache.stats.CacheStats statisticheCache() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache("ruoli");
        return cache.getNativeCache().stats();
    }

    @Test
    @DisplayName("La prima lettura dei ruoli va al database (miss), le successive NO: zero query e hit in cache")
    void lePiuLettureUsanoLaCache() {
        // PREPARO: nel database di test c'è già ADMIN; ne aggiungo altri e svuoto la memoria di Hibernate.
        ruolo("ISCRITTO");
        ruolo("ISTRUTTORE");
        ricarica();
        long hitPrima = statisticheCache().hitCount();
        long missPrima = statisticheCache().missCount();

        // ESEGUO la prima lettura: deve fare una query.
        statistiche().clear();
        List<Ruolo> primo = service.tuttiIRuoli();
        long queryPrimaLettura = statistiche().getPrepareStatementCount();

        // ESEGUO altre 5 letture: nessuna query.
        statistiche().clear();
        for (int i = 0; i < 5; i++) service.tuttiIRuoli();
        long queryLettureSuccessive = statistiche().getPrepareStatementCount();

        // VERIFICO
        assertThat(primo).hasSize(3);
        assertThat(queryPrimaLettura).isEqualTo(1);
        assertThat(queryLettureSuccessive).isZero();
        assertThat(statisticheCache().missCount() - missPrima).isEqualTo(1);
        assertThat(statisticheCache().hitCount() - hitPrima).isEqualTo(5);
    }

    @Test
    @DisplayName("Svuotando la cache la lettura successiva torna al database (come Cache::forget)")
    void dopoLoSvuotamentoRileggeDalDatabase() {
        ruolo("ISCRITTO");
        service.tuttiIRuoli();                       // riempie la cache
        cacheManager.getCache("ruoli").clear();      // la svuoto

        statistiche().clear();
        service.tuttiIRuoli();

        assertThat(statistiche().getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Il risultato con la cache è identico a quello senza: stessi ruoli, stesso ordine")
    void ilRisultatoNonCambia() {
        ruolo("ISCRITTO");
        ruolo("ISTRUTTORE");
        List<Ruolo> dalDatabase = service.tuttiIRuoli();     // miss
        List<Ruolo> dallaCache = service.tuttiIRuoli();      // hit

        assertThat(dallaCache).isEqualTo(dalDatabase);
        assertThat(dallaCache).extracting(r -> r.getNome()).containsExactly("ADMIN", "ISCRITTO", "ISTRUTTORE");
    }

    @Test
    @DisplayName("Aprire il form utente più volte: i ruoli si leggono dal database una volta sola")
    void formUtenteUsaLaCache() throws Exception {
        ruolo("ISCRITTO");
        ruolo("ISTRUTTORE");
        ricarica();

        mockMvc.perform(get("/utenti/nuovo").with(comeAdmin())).andExpect(status().isOk());      // riempie la cache
        long missDopoLaPrima = statisticheCache().missCount();
        long hitPrima = statisticheCache().hitCount();

        mockMvc.perform(get("/utenti/nuovo").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ISTRUTTORE")));                      // i ruoli si vedono ancora

        assertThat(statisticheCache().missCount()).isEqualTo(missDopoLaPrima);                   // nessun nuovo miss
        assertThat(statisticheCache().hitCount()).isEqualTo(hitPrima + 1);
    }
}
