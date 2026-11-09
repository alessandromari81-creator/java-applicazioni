/*
 * FILE: AbbonamentoControllerTest.java
 * A COSA SERVE: verifica la logica di business degli abbonamenti attraverso le pagine /abbonamenti:
 *   - creazione (data di fine e prezzo calcolati dal tipo);
 *   - le regole: solo ISCRITTO, niente passato, niente sovrapposizioni (ma il rinnovo il giorno dopo va bene);
 *   - il rinnovo (data di inizio proposta);
 *   - l'eliminazione solo dei non ancora iniziati;
 *   - cosa mostra la lista utenti e come lo storico degli abbonamenti protegge l'utente dalla cancellazione.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO. Le date sono sempre RELATIVE a "oggi"
 * (oggi.plusDays(...)), così i test funzionano in qualunque giorno vengano lanciati.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import it.palestra.service.AbbonamentoService;
import it.palestra.web.AbbonamentoForm;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class AbbonamentoControllerTest extends BaseWebTest {

    @Autowired AbbonamentoService service;   // per provare direttamente il riepilogo (vedi ultimi test)

    private final LocalDate oggi = LocalDate.now();

    // ---------------------------------------------------------------- METODI DI AIUTO

    private Utente creaIscritto(String nome, String cognome, String email) {
        return creaUtente(nome, cognome, email, ruolo("ISCRITTO"));
    }

    // Invia il form "nuovo abbonamento" con i dati indicati.
    private ResultActions inviaAbbonamento(Long utenteId, String tipo, LocalDate inizio) throws Exception {
        var richiesta = post("/abbonamenti").with(comeAdmin()).with(csrf());
        if (utenteId != null) richiesta.param("utenteId", utenteId.toString());
        if (tipo != null) richiesta.param("tipo", tipo);
        if (inizio != null) richiesta.param("dataInizio", inizio.toString());   // formato aaaa-mm-gg
        return mockMvc.perform(richiesta);
    }

    // ----------------------------------------------------------------- CREAZIONE

    @Test
    @DisplayName("La lista mostra gli abbonamenti con tipo, periodo, prezzo e stato")
    void laListaMostraGliAbbonamenti() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(get("/abbonamenti").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("abbonamenti/lista"))
                .andExpect(content().string(containsString("Rossi Mario")))
                .andExpect(content().string(containsString("Mensile")))
                .andExpect(content().string(containsString("40,00 €")))   // prezzo formattato all'italiana
                .andExpect(content().string(containsString("stato-attivo")));
    }

    @Test
    @DisplayName("Creare un abbonamento: la fine e il prezzo si calcolano dal tipo")
    void creareUnAbbonamento() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        // ESEGUO: si invia SOLO utente, tipo e data di inizio. Fine e prezzo non sono nel form.
        inviaAbbonamento(mario.getId(), "TRIMESTRALE", oggi)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/abbonamenti"))
                .andExpect(flash().attribute("messaggio", containsString("trimestrale creato")));

        // VERIFICO: quello che il server ha calcolato e salvato.
        ricarica();
        Abbonamento a = abbonamenti.findAll().get(0);
        assertThat(a.getTipo()).isEqualTo(TipoAbbonamento.TRIMESTRALE);
        assertThat(a.getDataInizio()).isEqualTo(oggi);
        assertThat(a.getDataFine()).isEqualTo(TipoAbbonamento.TRIMESTRALE.calcolaFine(oggi));
        assertThat(a.getPrezzo()).isEqualByComparingTo("105.00");
    }

    @Test
    @DisplayName("Campi obbligatori mancanti: un errore per campo")
    void campiMancanti() throws Exception {
        inviaAbbonamento(null, null, null)
                .andExpect(status().isOk())
                .andExpect(view().name("abbonamenti/form"))
                .andExpect(model().attributeHasFieldErrors("abbonamentoForm", "utenteId", "tipo", "dataInizio"));
    }

    @Test
    @DisplayName("Un tipo o una data scritti male danno un messaggio comprensibile, non un errore tecnico")
    void valoriNonConvertibili() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        // "BIMESTRALE" non è un tipo e "domani-forse" non è una data: richiesta costruita a mano.
        mockMvc.perform(post("/abbonamenti").with(comeAdmin()).with(csrf())
                        .param("utenteId", mario.getId().toString())
                        .param("tipo", "BIMESTRALE")
                        .param("dataInizio", "domani-forse"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "tipo", "typeMismatch"))
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "typeMismatch"))
                .andExpect(content().string(containsString("Valore non valido")))   // il testo di messages.properties
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Failed to convert"))));
    }

    // ------------------------------------------------------------- LE REGOLE DI BUSINESS

    @Test
    @DisplayName("Regola: solo chi ha il ruolo ISCRITTO può avere un abbonamento")
    void soloGliIscritti() throws Exception {
        // Un istruttore (non iscritto): la tendina non lo mostra, ma la richiesta è costruita a mano.
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        inviaAbbonamento(gino.getId(), "MENSILE", oggi)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "utenteId", "non-valido"))
                .andExpect(content().string(containsString("Solo gli utenti con ruolo ISCRITTO")));

        ricarica();
        assertThat(abbonamenti.count()).isZero();   // niente è stato salvato
    }

    @Test
    @DisplayName("Regola: l'utente scelto deve esistere")
    void utenteInesistente() throws Exception {
        inviaAbbonamento(999999L, "MENSILE", oggi)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "utenteId", "non-valido"));
    }

    @Test
    @DisplayName("Regola: la data di inizio non può essere nel passato")
    void nonNelPassato() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        inviaAbbonamento(mario.getId(), "MENSILE", oggi.minusDays(1))   // ieri
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "non-valida"))
                .andExpect(content().string(containsString("non può essere nel passato")));

        ricarica();
        assertThat(abbonamenti.count()).isZero();
    }

    @Test
    @DisplayName("Oggi come data di inizio è ammesso (il passato comincia da ieri)")
    void oggiVaBene() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        inviaAbbonamento(mario.getId(), "MENSILE", oggi).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Regola: un nuovo periodo che si sovrappone a uno esistente è rifiutato, con il primo giorno libero")
    void nonSiSovrappone() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento esistente = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);   // copre da oggi alla sua fine

        // Un nuovo mensile che parte tra 5 giorni cade DENTRO quello esistente.
        inviaAbbonamento(mario.getId(), "MENSILE", oggi.plusDays(5))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "non-valida"))
                .andExpect(content().string(containsString("Si sovrappone a un abbonamento già registrato")))
                // Il messaggio suggerisce il giorno da cui si può rinnovare: quello dopo la fine dell'esistente.
                .andExpect(content().string(containsString("primo giorno libero")));

        ricarica();
        assertThat(abbonamenti.count()).isEqualTo(1);   // c'è ancora solo l'esistente
        assertThat(esistente.getId()).isNotNull();
    }

    @Test
    @DisplayName("Regola: si sovrappone anche un nuovo periodo che INGLOBA uno esistente (es. annuale su un mensile futuro)")
    void unPeriodoCheIngloba() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(30));    // un mensile tra un mese

        // Un annuale che parte oggi copre anche quel mese: stessa persona, periodi che si incrociano.
        inviaAbbonamento(mario.getId(), "ANNUALE", oggi)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "non-valida"));
    }

    @Test
    @DisplayName("Rinnovo: il giorno DOPO la scadenza è ammesso (i periodi si toccano ma non si sovrappongono)")
    void ilRinnovoIlGiornoDopo() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        inviaAbbonamento(mario.getId(), "MENSILE", attuale.getDataFine().plusDays(1))
                .andExpect(status().is3xxRedirection());

        ricarica();
        assertThat(abbonamenti.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rinnovo: partire proprio nel giorno di scadenza è già una sovrapposizione (di un giorno)")
    void ilGiornoDiScadenzaEGiaSovrapposto() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        // La fine è INCLUSA: nel suo ultimo giorno l'abbonamento è ancora valido.
        inviaAbbonamento(mario.getId(), "MENSILE", attuale.getDataFine())
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("abbonamentoForm", "dataInizio", "non-valida"));
    }

    @Test
    @DisplayName("Gli abbonamenti di PERSONE diverse non si bloccano a vicenda")
    void altriUtentiNonSiBloccano() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");
        creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);

        inviaAbbonamento(laura.getId(), "MENSILE", oggi)    // stesso periodo, altra persona
                .andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------------- IL RINNOVO

    // Legge dal model l'AbbonamentoForm preparato dal controller per la pagina "nuovo".
    private AbbonamentoForm formProposto(Long utenteId) throws Exception {
        return (AbbonamentoForm) mockMvc.perform(get("/abbonamenti/nuovo")
                        .param("utenteId", utenteId.toString()).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("abbonamenti/form"))
                .andReturn().getModelAndView().getModel().get("abbonamentoForm");
    }

    @Test
    @DisplayName("Il form propone come inizio il giorno dopo la fine dell'abbonamento in corso")
    void proponeIlGiornoDopoLaFine() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        AbbonamentoForm form = formProposto(mario.getId());

        assertThat(form.getUtenteId()).isEqualTo(mario.getId());                       // l'iscritto è già scelto
        assertThat(form.getDataInizio()).isEqualTo(attuale.getDataFine().plusDays(1)); // il rinnovo parte dopo
    }

    @Test
    @DisplayName("Senza abbonamenti, o con uno già scaduto, il form propone oggi")
    void proponeOggiSeNonCEUnAbbonamentoValido() throws Exception {
        Utente senza = creaIscritto("Mario", "Rossi", "mario@example.com");
        Utente scaduto = creaIscritto("Laura", "Bianchi", "laura@example.com");
        creaAbbonamento(scaduto, TipoAbbonamento.MENSILE, oggi.minusDays(90));   // finito da un pezzo

        assertThat(formProposto(senza.getId()).getDataInizio()).isEqualTo(oggi);
        assertThat(formProposto(scaduto.getId()).getDataInizio()).isEqualTo(oggi);
    }

    // ------------------------------------------------------------------ ELIMINAZIONE

    @Test
    @DisplayName("Un abbonamento NON ancora iniziato si può eliminare")
    void eliminareUnoFuturo() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(10));

        mockMvc.perform(post("/abbonamenti/" + futuro.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("messaggio", "Abbonamento eliminato"));

        ricarica();
        assertThat(abbonamenti.findById(futuro.getId())).isEmpty();
    }

    @Test
    @DisplayName("Un abbonamento ATTIVO non si può eliminare")
    void nonSiEliminaUnoAttivo() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attivo = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);   // inizia oggi: è già attivo

        mockMvc.perform(post("/abbonamenti/" + attivo.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("errore", containsString("solo gli abbonamenti non ancora iniziati")));

        ricarica();
        assertThat(abbonamenti.findById(attivo.getId())).isPresent();
    }

    @Test
    @DisplayName("Un abbonamento SCADUTO non si può eliminare: resta nello storico")
    void nonSiEliminaUnoScaduto() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento scaduto = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusDays(90));

        mockMvc.perform(post("/abbonamenti/" + scaduto.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("errore", containsString("restano nello storico")));

        ricarica();
        assertThat(abbonamenti.findById(scaduto.getId())).isPresent();
    }

    @Test
    @DisplayName("Eliminare un abbonamento inesistente dà 404")
    void eliminareUnoInesistente() throws Exception {
        mockMvc.perform(post("/abbonamenti/999999/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(model().attribute("dettaglio", "Abbonamento 999999 non trovato"));
    }

    // -------------------------------------------- LO STORICO PROTEGGE L'UTENTE DALLA CANCELLAZIONE

    @Test
    @DisplayName("Un utente con abbonamenti registrati non si può eliminare")
    void nonSiEliminaUnUtenteConAbbonamenti() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusDays(90));   // anche se scaduto, è storico

        mockMvc.perform(post("/utenti/" + mario.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("errore", containsString("1 abbonamenti registrati")));

        ricarica();
        assertThat(utenti.findById(mario.getId())).isPresent();
    }

    @Test
    @DisplayName("A chi ha abbonamenti non si può togliere il ruolo ISCRITTO")
    void nonSiTogliIscrittoSeHaAbbonamenti() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        // Gli lascio solo il ruolo ISTRUTTORE (cioè gli tolgo ISCRITTO).
        mockMvc.perform(post("/utenti/" + mario.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("ruoliIds", ruolo("ISTRUTTORE").getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/form"))
                .andExpect(content().string(containsString("Non puoi togliere il ruolo ISCRITTO: ha 1 abbonamenti registrati")));
    }

    // --------------------------------------------------------------- LISTA UTENTI E RIEPILOGO

    @Test
    @DisplayName("La lista utenti mostra lo stato dell'abbonamento degli iscritti")
    void laListaUtentiMostraLoStato() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);

        mockMvc.perform(get("/utenti").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stato-attivo")))
                // La cella dell'abbonamento è un LINK alla pagina degli abbonamenti di quell'utente...
                .andExpect(content().string(containsString("/abbonamenti/utente/" + mario.getId())))
                // ...e il vecchio pulsante "Abbonamento" nelle azioni non c'è più.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Abbonamento</a>"))));
    }

    @Test
    @DisplayName("Riepilogo: tra più abbonamenti sceglie l'attivo, poi il prossimo, poi l'ultimo scaduto")
    void ilRiepilogoSceglieIlPiuSignificativo() {
        // PREPARO tre iscritti con storie diverse.
        Utente conAttivo = creaIscritto("A", "Attivo", "a@example.com");
        creaAbbonamento(conAttivo, TipoAbbonamento.MENSILE, oggi.minusDays(90));            // vecchio, scaduto
        Abbonamento attivo = creaAbbonamento(conAttivo, TipoAbbonamento.ANNUALE, oggi.minusDays(5));   // in corso
        creaAbbonamento(conAttivo, TipoAbbonamento.MENSILE, oggi.plusDays(400));            // futuro lontano

        Utente conFuturo = creaIscritto("F", "Futuro", "f@example.com");
        creaAbbonamento(conFuturo, TipoAbbonamento.MENSILE, oggi.minusDays(200));           // scaduto
        Abbonamento prossimo = creaAbbonamento(conFuturo, TipoAbbonamento.MENSILE, oggi.plusDays(20));
        creaAbbonamento(conFuturo, TipoAbbonamento.MENSILE, oggi.plusDays(100));            // più lontano

        Utente soloScaduti = creaIscritto("S", "Scaduti", "s@example.com");
        creaAbbonamento(soloScaduti, TipoAbbonamento.MENSILE, oggi.minusDays(300));
        Abbonamento ultimoScaduto = creaAbbonamento(soloScaduti, TipoAbbonamento.MENSILE, oggi.minusDays(100));

        ricarica();
        Map<Long, Abbonamento> riepilogo = service.riepilogoPerUtente();

        assertThat(riepilogo.get(conAttivo.getId()).getId()).isEqualTo(attivo.getId());        // l'attivo vince su tutto
        assertThat(riepilogo.get(conFuturo.getId()).getId()).isEqualTo(prossimo.getId());      // il futuro PIÙ VICINO
        assertThat(riepilogo.get(soloScaduti.getId()).getId()).isEqualTo(ultimoScaduto.getId()); // lo scaduto PIÙ RECENTE
    }

    // ------------------------------------------------------------------------- SICUREZZA

    @Test
    @DisplayName("Le pagine abbonamenti sono riservate agli ADMIN")
    void soloGliAdmin() throws Exception {
        mockMvc.perform(get("/abbonamenti")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/abbonamenti").with(user("mario@example.com").roles("ISCRITTO")))
                .andExpect(status().isForbidden());
    }
}
