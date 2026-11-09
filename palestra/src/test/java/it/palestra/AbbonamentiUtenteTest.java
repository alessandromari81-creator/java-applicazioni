/*
 * FILE: AbbonamentiUtenteTest.java
 * A COSA SERVE: verifica la pagina degli abbonamenti di UN singolo utente (/abbonamenti/utente/{id}): lo storico,
 * il rinnovo con la data proposta, l'aggiunta di un abbonamento, l'eliminazione con ritorno alla stessa pagina e il
 * fatto che l'utente si ricava dall'URL e non dal form.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO. Le date sono RELATIVE a oggi.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import it.palestra.model.Abbonamento;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import it.palestra.web.AbbonamentoForm;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbbonamentiUtenteTest extends BaseWebTest {

    private final LocalDate oggi = LocalDate.now();

    private Utente creaIscritto(String nome, String cognome, String email) {
        return creaUtente(nome, cognome, email, ruolo("ISCRITTO"));
    }

    // -------------------------------------------------------------------------- LA PAGINA

    @Test
    @DisplayName("La pagina mostra lo storico di QUELL'utente, dal più recente, e non quello degli altri")
    void mostraSoloLoStoricoDellUtente() throws Exception {
        // PREPARO: Mario con due abbonamenti, Laura con uno suo.
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");
        Abbonamento vecchio = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusDays(90));
        Abbonamento recente = creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);
        creaAbbonamento(laura, TipoAbbonamento.TRIMESTRALE, oggi);

        // ESEGUO + VERIFICO: nel model ci sono solo i due di Mario, con il più recente per primo.
        @SuppressWarnings("unchecked")
        List<Abbonamento> mostrati = (List<Abbonamento>) mockMvc.perform(get("/abbonamenti/utente/" + mario.getId()).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("abbonamenti/utente"))
                .andExpect(content().string(containsString("Rossi Mario")))
                .andExpect(content().string(not(containsString("Bianchi"))))      // niente dati di Laura
                .andReturn().getModelAndView().getModel().get("abbonamenti");

        assertThat(mostrati).extracting(Abbonamento::getId).containsExactly(recente.getId(), vecchio.getId());
    }

    @Test
    @DisplayName("Il form della pagina è già pronto per il rinnovo: utente scelto e inizio = giorno dopo la fine")
    void ilFormEGiaPronto() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        AbbonamentoForm form = (AbbonamentoForm) mockMvc.perform(get("/abbonamenti/utente/" + mario.getId()).with(comeAdmin()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("abbonamentoForm");

        assertThat(form.getUtenteId()).isEqualTo(mario.getId());
        assertThat(form.getDataInizio()).isEqualTo(attuale.getDataFine().plusDays(1));
    }

    @Test
    @DisplayName("Un utente senza abbonamenti vede 'nessun abbonamento' e il form con inizio oggi")
    void utenteSenzaAbbonamenti() throws Exception {
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");

        mockMvc.perform(get("/abbonamenti/utente/" + laura.getId()).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nessun abbonamento registrato")))
                .andExpect(content().string(containsString("Aggiungi abbonamento")))
                .andExpect(model().attribute("abbonamentoForm",
                        org.hamcrest.Matchers.hasProperty("dataInizio", org.hamcrest.Matchers.is(oggi))));
    }

    @Test
    @DisplayName("Chi non è ISCRITTO vede una spiegazione e NON il form")
    void utenteNonIscritto() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(get("/abbonamenti/utente/" + gino.getId()).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("non ha il ruolo ISCRITTO")))
                .andExpect(content().string(not(containsString("Aggiungi abbonamento"))));
    }

    @Test
    @DisplayName("Un utente inesistente dà 404")
    void utenteInesistente() throws Exception {
        mockMvc.perform(get("/abbonamenti/utente/999999").with(comeAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(model().attribute("dettaglio", "Utente 999999 non trovato"));
    }

    @Test
    @DisplayName("La pagina è riservata agli ADMIN")
    void soloGliAdmin() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        String url = "/abbonamenti/utente/" + mario.getId();

        mockMvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get(url).with(user("x@example.com").roles("ISCRITTO"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------- AGGIUNGERE UN ABBONAMENTO

    @Test
    @DisplayName("Aggiungere un abbonamento dalla pagina: si salva e si torna alla pagina dell'utente")
    void aggiungereDallaPagina() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(post("/abbonamenti/utente/" + mario.getId()).with(comeAdmin()).with(csrf())
                        .param("tipo", "TRIMESTRALE")
                        .param("dataInizio", oggi.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/abbonamenti/utente/" + mario.getId()))     // di nuovo QUI, non all'elenco generale
                .andExpect(flash().attribute("messaggio", containsString("trimestrale creato")));

        ricarica();
        assertThat(abbonamenti.findByUtenteIdOrderByDataInizioDesc(mario.getId())).hasSize(1);
    }

    @Test
    @DisplayName("L'utente viene dall'URL: un utenteId diverso nei dati inviati viene ignorato")
    void lUtenteVieneDallUrl() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");

        // ESEGUO: la richiesta è per la pagina di Mario, ma nei dati c'è l'id di Laura (richiesta costruita a mano).
        mockMvc.perform(post("/abbonamenti/utente/" + mario.getId()).with(comeAdmin()).with(csrf())
                        .param("utenteId", laura.getId().toString())
                        .param("tipo", "MENSILE")
                        .param("dataInizio", oggi.toString()))
                .andExpect(status().is3xxRedirection());

        // VERIFICO: l'abbonamento è di Mario; Laura non ne ha ricevuti.
        ricarica();
        assertThat(abbonamenti.findByUtenteIdOrderByDataInizioDesc(mario.getId())).hasSize(1);
        assertThat(abbonamenti.findByUtenteIdOrderByDataInizioDesc(laura.getId())).isEmpty();
    }

    @Test
    @DisplayName("Con un errore (sovrapposizione) si rimostra la STESSA pagina, con lo storico e l'errore sul campo")
    void erroreNellaPagina() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(post("/abbonamenti/utente/" + mario.getId()).with(comeAdmin()).with(csrf())
                        .param("tipo", "MENSILE")
                        .param("dataInizio", oggi.plusDays(3).toString()))    // dentro quello esistente
                .andExpect(status().isOk())
                .andExpect(view().name("abbonamenti/utente"))
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "non-valida"))
                .andExpect(content().string(containsString("Si sovrappone a un abbonamento già registrato")))
                // La pagina è completa: c'è ancora lo storico di Mario (non una pagina vuota o rotta).
                .andExpect(model().attribute("abbonamenti", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(content().string(containsString("Rossi Mario")));
    }

    @Test
    @DisplayName("Anche dalla pagina: un utente non iscritto non può avere un abbonamento (richiesta costruita a mano)")
    void nonIscrittoDallaPagina() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(post("/abbonamenti/utente/" + gino.getId()).with(comeAdmin()).with(csrf())
                        .param("tipo", "MENSILE").param("dataInizio", oggi.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "utenteId", "non-valido"));

        ricarica();
        assertThat(abbonamenti.count()).isZero();
    }

    // ---------------------------------------------------------------------- ELIMINARE E TORNARE QUI

    @Test
    @DisplayName("Eliminare dalla pagina dell'utente riporta alla pagina dell'utente")
    void eliminareETornareQui() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(10));

        mockMvc.perform(post("/abbonamenti/" + futuro.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("daUtente", "true"))
                .andExpect(redirectedUrl("/abbonamenti/utente/" + mario.getId()))
                .andExpect(flash().attribute("messaggio", "Abbonamento eliminato"));

        ricarica();
        assertThat(abbonamenti.findById(futuro.getId())).isEmpty();
    }

    @Test
    @DisplayName("Un'eliminazione rifiutata (abbonamento attivo) riporta comunque alla pagina dell'utente, con l'errore")
    void eliminazioneRifiutataTornaQui() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attivo = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(post("/abbonamenti/" + attivo.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("daUtente", "true"))
                .andExpect(redirectedUrl("/abbonamenti/utente/" + mario.getId()))
                .andExpect(flash().attribute("errore", containsString("solo gli abbonamenti non ancora iniziati")));
    }

    @Test
    @DisplayName("Senza il parametro daUtente, dopo l'eliminazione si va all'elenco generale (comportamento di prima)")
    void senzaDaUtenteVaAllElenco() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(10));

        mockMvc.perform(post("/abbonamenti/" + futuro.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/abbonamenti"));
    }

    @Test
    @DisplayName("L'indirizzo di ritorno non si può pilotare: un valore di 'daUtente' non booleano è respinto con 400")
    void nessunOpenRedirect() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(10));

        // Provo a infilare un indirizzo esterno al posto di true/false. "daUtente" è un BOOLEANO: qualunque altro
        // valore non si può convertire e Spring rifiuta la richiesta con 400 (Bad Request). Non c'è mai un redirect
        // verso un indirizzo scelto da chi invia la richiesta: quello di ritorno lo costruisce sempre il server.
        mockMvc.perform(post("/abbonamenti/" + futuro.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("daUtente", "https://sito-cattivo.example"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Location"));

        // E l'abbonamento non è stato toccato: la richiesta è stata respinta prima di fare qualsiasi cosa.
        ricarica();
        assertThat(abbonamenti.findById(futuro.getId())).isPresent();
    }
}
