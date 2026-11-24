/*
 * FILE: IscrizioneCorsiTest.java
 * A COSA SERVE: verifica la regola «per ISCRIVERE un iscritto a un corso serve un abbonamento attivo oggi»:
 *   - vale per i corsi che si AGGIUNGONO, dalla pagina «Corsi» del pannello e dalla API; creare un utente con corsi dalla API è rifiutato (non può avere ancora abbonamenti);
 *   - «attivo oggi» include il primo e l'ultimo giorno; un abbonamento futuro o scaduto non basta;
 *   - i corsi già assegnati RESTANO anche se l'abbonamento è scaduto (salvare il modulo dell'utente non li tocca, e togliere corsi si può sempre);
 *   - la regola vale per TUTTI: chi non ha il ruolo ISCRITTO (che quindi non può avere abbonamenti) non si iscrive ai corsi finché non riceve ruolo e abbonamento;
 *   - l'avviso «abbonamento non attivo» compare nella lista utenti, nella pagina «Corsi» dell'utente e nell'area personale solo quando serve.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Abbonamento;
import it.palestra.model.Corso;
import it.palestra.model.Ruolo;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class IscrizioneCorsiTest extends BaseApiTest {

    private final LocalDate oggi = LocalDate.now();

    // ------------------------------------------------------------------ METODI DI AIUTO

    // L'utente è già iscritto ai corsi indicati (assegnati direttamente, come i dati di prova già presenti nel database).
    private void iscrivi(Utente u, Corso... corsiScelti) {
        u.setCorsi(new HashSet<>(Set.of(corsiScelti)));
        utenti.save(u);
        ricarica();
    }

    // Un abbonamento MENSILE che finisce esattamente oggi (ultimo giorno di validità).
    private Abbonamento abbonamentoCheFinisceOggi(Utente u) {
        Abbonamento a = creaAbbonamento(u, TipoAbbonamento.MENSILE, oggi.minusMonths(1).plusDays(1));
        assertThat(a.getDataFine()).isEqualTo(oggi);
        return a;
    }

    // Il modulo di modifica del pannello per un ISCRITTO, scrivendo un nome diverso. Il modulo NON manda più i corsi: salvarlo non li tocca.
    private MockHttpServletRequestBuilder modificaConNome(Utente u, String nome) {
        return post("/utenti/" + u.getId()).with(comeAdmin()).with(csrf())
                .param("nome", nome).param("cognome", u.getCognome()).param("email", u.getEmail())
                .param("ruoliIds", ruolo("ISCRITTO").getId().toString());
    }

    // Iscrizione dalla pagina «Corsi» dell'utente (pulsante «Iscrivi...» dopo la conferma).
    private MockHttpServletRequestBuilder iscriviDalPannello(Utente u, Corso c) {
        return post("/utenti/" + u.getId() + "/corsi/" + c.getId() + "/iscrivi").with(comeAdmin()).with(csrf());
    }

    // Disiscrizione dalla pagina «Corsi» dell'utente (pulsante «Disiscrivi...» dopo la conferma).
    private MockHttpServletRequestBuilder disiscriviDalPannello(Utente u, Corso c) {
        return post("/utenti/" + u.getId() + "/corsi/" + c.getId() + "/disiscrivi").with(comeAdmin()).with(csrf());
    }

    private ResultActions modificaViaApi(Utente u, Corso... corsiScelti) throws Exception {
        String ids = String.join(",", Arrays.stream(corsiScelti).map(c -> c.getId().toString()).toList());
        return mockMvc.perform(conJson(put("/api/utenti/" + u.getId()),
                "{\"nome\":\"" + u.getNome() + "\",\"cognome\":\"" + u.getCognome() + "\",\"email\":\"" + u.getEmail()
                        + "\",\"ruoli\":[\"ISCRITTO\"],\"corsiIds\":[" + ids + "]}"));
    }

    private Set<String> nomiCorsiDi(Utente u) {
        ricarica();
        return utenti.findWithRelazioniById(u.getId()).orElseThrow().getCorsi().stream()
                .map(c -> c.getNome()).collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------- SENZA ABBONAMENTO ATTIVO: NON SI AGGIUNGE

    @Test
    @DisplayName("Pannello: senza abbonamento non si iscrive a un corso (messaggio rosso, niente salvato)")
    void pannelloSenzaAbbonamento() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(iscriviDalPannello(mario, yoga))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti/" + mario.getId() + "/corsi"))
                .andExpect(flash().attribute("errore", containsString("serve un abbonamento attivo oggi")))
                .andExpect(flash().attribute("errore", containsString("Yoga")));

        assertThat(nomiCorsiDi(mario)).isEmpty();
    }

    @Test
    @DisplayName("API: senza abbonamento il PUT che aggiunge un corso è un 400 con l'errore su corsiIds")
    void apiSenzaAbbonamento() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");

        modificaViaApi(mario, yoga)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.corsiIds[0]").value(containsString("serve un abbonamento attivo oggi")));

        assertThat(nomiCorsiDi(mario)).isEmpty();
    }

    @Test
    @DisplayName("Creare un iscritto già con dei corsi è rifiutato dalla API; il modulo del pannello non gestisce i corsi e ignora quelli inviati")
    void creazioneConCorsi() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Ruolo iscritto = ruolo("ISCRITTO");

        // Pannello: il modulo non ha più i corsi. Se una richiesta costruita a mano ne manda, vengono ignorati: l'utente nasce senza corsi.
        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Pia").param("cognome", "Bianchi").param("email", "pia@example.com")
                        .param("ruoliIds", iscritto.getId().toString()).param("corsiIds", yoga.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(nomiCorsiDi(utenti.findByEmailIgnoreCase("pia@example.com").orElseThrow())).isEmpty();

        // API: il PUT/POST sostituisce i corsi, quindi la regola vale e la creazione con corsi è rifiutata.
        mockMvc.perform(conJson(post("/api/utenti"),
                        "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"mario@example.com\",\"ruoli\":[\"ISCRITTO\"],\"corsiIds\":[" + yoga.getId() + "]}"))
                .andExpect(status().isBadRequest());
        assertThat(utenti.findByEmailIgnoreCase("mario@example.com")).isEmpty();

        mockMvc.perform(conJson(post("/api/utenti"),
                        "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"mario@example.com\",\"ruoli\":[\"ISCRITTO\"],\"corsiIds\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Il messaggio elenca solo i corsi da AGGIUNGERE (in ordine alfabetico), non quelli già assegnati")
    void messaggioConIcorsiDaAggiungere() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");
        Corso spinning = creaCorso("Spinning");
        Corso pilates = creaCorso("Pilates");
        iscrivi(mario, yoga);

        modificaViaApi(mario, yoga, spinning, pilates)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.corsiIds[0]").value(containsString("corsi da aggiungere: Pilates, Spinning)")))
                .andExpect(jsonPath("$.campi.corsiIds[0]").value(not(containsString("Yoga"))));
    }

    // ------------------------------------------------------------------ «ATTIVO OGGI»

    @Test
    @DisplayName("Con un abbonamento attivo oggi si può iscrivere a un corso, dal pannello e dalla API")
    void conAbbonamentoAttivo() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);                 // il primo giorno è già attivo
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");

        mockMvc.perform(iscriviDalPannello(mario, yoga))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Iscritto al corso Yoga"));
        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");

        modificaViaApi(mario, yoga, pilates).andExpect(status().isOk());
        assertThat(nomiCorsiDi(mario)).containsExactlyInAnyOrder("Yoga", "Pilates");
    }

    @Test
    @DisplayName("L'ULTIMO giorno di validità l'abbonamento è ancora attivo (estremi inclusi)")
    void ultimoGiornoIncluso() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        abbonamentoCheFinisceOggi(mario);
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(iscriviDalPannello(mario, yoga)).andExpect(status().is3xxRedirection());

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
    }

    @Test
    @DisplayName("Un abbonamento FUTURO (non ancora iniziato) non basta: serve che sia attivo oggi")
    void abbonamentoFuturo() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(1));
        Corso yoga = creaCorso("Yoga");

        modificaViaApi(mario, yoga).andExpect(status().isBadRequest());

        assertThat(nomiCorsiDi(mario)).isEmpty();
    }

    @Test
    @DisplayName("Un abbonamento SCADUTO (finito ieri) non basta")
    void abbonamentoScaduto() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Abbonamento a = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusMonths(1));      // finisce ieri
        assertThat(a.getDataFine()).isEqualTo(oggi.minusDays(1));
        Corso yoga = creaCorso("Yoga");

        modificaViaApi(mario, yoga).andExpect(status().isBadRequest());
    }

    // --------------------------------------------------- CORSI GIÀ ASSEGNATI: RESTANO

    @Test
    @DisplayName("Abbonamento scaduto: i corsi già assegnati RESTANO; salvare il modulo dell'utente (anche cambiando altri dati) non li tocca")
    void iCorsiGiaAssegnatiRestano() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");
        iscrivi(mario, yoga);                                                  // iscritto senza alcun abbonamento (come i dati di prova)

        mockMvc.perform(modificaConNome(mario, "Marione")).andExpect(status().is3xxRedirection());

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
        assertThat(utenti.findById(mario.getId()).orElseThrow().getNome()).isEqualTo("Marione");
        modificaViaApi(mario, yoga).andExpect(status().isOk());                 // anche dalla API
    }

    @Test
    @DisplayName("Abbonamento scaduto: togliere un corso si può sempre; aggiungerne uno nuovo no")
    void togliereSiAggiungereNo() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusMonths(3));
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        iscrivi(mario, yoga, pilates);

        mockMvc.perform(disiscriviDalPannello(mario, pilates)).andExpect(status().is3xxRedirection());      // toglie Pilates: si può sempre
        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");

        mockMvc.perform(iscriviDalPannello(mario, pilates))                                            // lo rimette: è un corso da AGGIUNGERE
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errore", containsString("serve un abbonamento attivo oggi")));
        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
    }

    // ------------------------------------------------------------ CHI NON HA IL RUOLO ISCRITTO

    @Test
    @DisplayName("La regola vale per TUTTI: un istruttore senza il ruolo ISCRITTO (che quindi non può avere abbonamenti) non si iscrive ai corsi, dal pannello e dalla API")
    void ancheChiNonEIscrittoHaBisognoDellAbbonamento() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(iscriviDalPannello(gino, yoga))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errore", containsString("servono il ruolo ISCRITTO e un abbonamento attivo oggi")))
                .andExpect(flash().attribute("errore", containsString("Yoga")));
        mockMvc.perform(conJson(put("/api/utenti/" + gino.getId()),
                        "{\"nome\":\"Gino\",\"cognome\":\"Verdi\",\"email\":\"gino@example.com\",\"ruoli\":[\"ISTRUTTORE\"],\"corsiIds\":[" + yoga.getId() + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.corsiIds[0]").value(containsString("servono il ruolo ISCRITTO e un abbonamento attivo oggi")));

        assertThat(nomiCorsiDi(gino)).isEmpty();
    }

    @Test
    @DisplayName("Un istruttore che è anche ISCRITTO e ha un abbonamento attivo oggi si iscrive ai corsi come tutti")
    void istruttoreIscrittoConAbbonamento() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"), ruolo("ISCRITTO"));
        creaAbbonamento(gino, TipoAbbonamento.MENSILE, oggi);
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(iscriviDalPannello(gino, yoga)).andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Iscritto al corso Yoga"));

        assertThat(nomiCorsiDi(gino)).containsExactly("Yoga");
    }

    @Test
    @DisplayName("Chi non ha il ruolo ISCRITTO e ha già dei corsi (dati vecchi) li tiene e li può togliere; non ne aggiunge altri")
    void corsiGiaAssegnatiAChiNonEIscritto() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        iscrivi(gino, yoga);

        mockMvc.perform(iscriviDalPannello(gino, pilates)).andExpect(flash().attribute("errore", containsString("servono il ruolo ISCRITTO")));
        assertThat(nomiCorsiDi(gino)).containsExactly("Yoga");

        mockMvc.perform(disiscriviDalPannello(gino, yoga)).andExpect(status().is3xxRedirection());
        assertThat(nomiCorsiDi(gino)).isEmpty();
    }

    // ------------------------------------------------------------------------- AVVISI

    @Test
    @DisplayName("Lista utenti: l'etichetta «abbonamento non attivo» compare solo per chi ha corsi e non ha un abbonamento attivo")
    void avvisoNellaLista() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Utente senza = creaUtente("Senza", "Abbonamento", "senza@example.com", ruolo("ISCRITTO"));
        iscrivi(senza, yoga);
        Utente scaduto = creaUtente("Scaduto", "Abbonato", "scaduto@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(scaduto, TipoAbbonamento.MENSILE, oggi.minusMonths(3));
        iscrivi(scaduto, yoga);
        Utente attivo = creaUtente("Attivo", "Abbonato", "attivo@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(attivo, TipoAbbonamento.ANNUALE, oggi);
        iscrivi(attivo, yoga);
        creaUtente("Nessun", "Corso", "nessuncorso@example.com", ruolo("ISCRITTO"));

        String html = mockMvc.perform(get("/utenti").with(comeAdmin())).andReturn().getResponse().getContentAsString();

        assertThat(html.split("abbonamento non attivo", -1).length - 1).isEqualTo(2);       // solo «senza» e «scaduto»
    }

    @Test
    @DisplayName("Pagina «Corsi» dell'utente: senza abbonamento attivo c'è l'avviso e i pulsanti «Iscrivi» sono spenti; con abbonamento attivo no")
    void avvisoNellaPaginaCorsi() throws Exception {
        creaCorso("Yoga");
        Utente senza = creaUtente("Senza", "Abbonamento", "senza@example.com", ruolo("ISCRITTO"));
        Utente attivo = creaUtente("Attivo", "Abbonato", "attivo@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(attivo, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(get("/utenti/" + senza.getId() + "/corsi").with(comeAdmin()))
                .andExpect(content().string(containsString("non ha un abbonamento attivo oggi")))
                .andExpect(content().string(containsString("disabled")));
        mockMvc.perform(get("/utenti/" + attivo.getId() + "/corsi").with(comeAdmin()))
                .andExpect(content().string(not(containsString("non ha un abbonamento attivo oggi"))))
                .andExpect(content().string(not(containsString("disabled"))));
    }

    @Test
    @DisplayName("Area personale: l'iscritto con corsi e abbonamento non attivo vede l'avviso; con abbonamento attivo o senza corsi no")
    void avvisoNellArea() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Utente scaduto = creaUtente("Scaduto", "Abbonato", "scaduto@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(scaduto, TipoAbbonamento.MENSILE, oggi.minusMonths(3));
        iscrivi(scaduto, yoga);
        Utente attivo = creaUtente("Attivo", "Abbonato", "attivo@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(attivo, TipoAbbonamento.MENSILE, oggi);
        iscrivi(attivo, yoga);
        creaUtente("Nessun", "Corso", "nessuncorso@example.com", ruolo("ISCRITTO"));

        for (String url : new String[] {"/area", "/area/corsi"}) {
            mockMvc.perform(get(url).with(comeUtente("scaduto@example.com", "ISCRITTO")))
                    .andExpect(content().string(containsString("Il tuo abbonamento non è attivo")));
            mockMvc.perform(get(url).with(comeUtente("attivo@example.com", "ISCRITTO")))
                    .andExpect(content().string(not(containsString("Il tuo abbonamento non è attivo"))));
            mockMvc.perform(get(url).with(comeUtente("nessuncorso@example.com", "ISCRITTO")))
                    .andExpect(content().string(not(containsString("Il tuo abbonamento non è attivo"))));
        }
    }
}
