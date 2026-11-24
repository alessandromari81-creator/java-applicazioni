/*
 * FILE: CorsiUtenteTest.java
 * A COSA SERVE: verifica la pagina «Corsi» di un utente (solo ADMIN), che ha preso il posto dell'elenco di caselle nel modulo utente:
 *   - accessi: l'anonimo va al login, chi non è admin riceve 403, un utente o un corso inesistente è un 404, le POST richiedono il token CSRF;
 *   - la pagina mostra i corsi a cui l'utente è iscritto e quelli a cui può iscriversi (non quelli già seguiti né quelli che tiene lui);
 *   - la lista dei disponibili ha ricerca (con % e _ come caratteri normali), ordine, paginazione e torna all'ultima pagina se si chiede oltre;
 *   - iscrivere e disiscrivere: messaggi, stato della lista conservato nell'indirizzo di ritorno, «già iscritto» e «non iscritto» rifiutati;
 *   - la finestra di conferma: il codice HTML che la apre (data-conferma, <dialog>) e lo script servito senza login;
 *   - nella lista utenti il pulsante «Corsi (N)» porta alla pagina e non elenca più i nomi dei corsi.
 * Le regole sull'abbonamento e sull'istruttore sono provate in IscrizioneCorsiTest e IstruttoriTest.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsiUtenteTest extends BaseWebTest {

    // ------------------------------------------------------------------ METODI DI AIUTO

    // Un iscritto con un abbonamento attivo oggi: può essere iscritto ai corsi.
    private Utente iscrittoAbbonato() {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, LocalDate.now());
        return mario;
    }

    private String indirizzo(Utente u) {
        return "/utenti/" + u.getId() + "/corsi";
    }

    private Set<String> nomiCorsiDi(Utente u) {
        ricarica();
        return utenti.findWithRelazioniById(u.getId()).orElseThrow().getCorsi().stream()
                .map(c -> c.getNome()).collect(Collectors.toSet());
    }

    private void iscrivereDirettamente(Utente u, Corso... corsiScelti) {
        u.setCorsi(new HashSet<>(Set.of(corsiScelti)));
        utenti.save(u);
        ricarica();
    }

    // ------------------------------------------------------------------ ACCESSI

    @Test
    @DisplayName("L'anonimo è mandato al login; un ISCRITTO o un ISTRUTTORE riceve 403, sia sulla pagina sia sulle POST")
    void accessi() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(get(indirizzo(mario)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        for (String ruoloDelloUtente : new String[] {"ISCRITTO", "ISTRUTTORE"}) {
            mockMvc.perform(get(indirizzo(mario)).with(comeUtente("mario@example.com", ruoloDelloUtente)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi")
                            .with(comeUtente("mario@example.com", ruoloDelloUtente)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(nomiCorsiDi(mario)).isEmpty();
    }

    @Test
    @DisplayName("Le POST senza il token CSRF sono rifiutate")
    void servePerIlTokenCsrf() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()))
                .andExpect(status().isForbidden());

        assertThat(nomiCorsiDi(mario)).isEmpty();
    }

    @Test
    @DisplayName("Un utente o un corso inesistente è un 404")
    void inesistenti() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(get("/utenti/999999/corsi").with(comeAdmin())).andExpect(status().isNotFound());
        mockMvc.perform(post("/utenti/999999/corsi/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(indirizzo(mario) + "/999999/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(indirizzo(mario) + "/999999/disiscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ LA PAGINA

    @Test
    @DisplayName("La pagina mostra i corsi seguiti e quelli disponibili, senza i seguiti e senza quelli che l'utente tiene come istruttore")
    void cosaMostra() throws Exception {
        // PREPARO: Gino è istruttore di Zumba, segue Yoga e potrebbe seguire Pilates e Spinning.
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        creaCorso("Pilates");
        creaCorso("Spinning");
        Corso zumba = creaCorso("Zumba");
        zumba.setIstruttore(gino);
        corsi.save(zumba);
        iscrivereDirettamente(gino, yoga);

        // ESEGUO
        String html = mockMvc.perform(get(indirizzo(gino)).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/corsi"))
                .andReturn().getResponse().getContentAsString();

        // VERIFICO: la sezione dei corsi seguiti contiene Yoga; quella dei disponibili Pilates e Spinning; Zumba (che tiene) da nessuna parte.
        int inizioDisponibili = html.indexOf("Corsi a cui può essere iscritto");
        assertThat(inizioDisponibili).isPositive();
        String seguiti = html.substring(0, inizioDisponibili);
        String disponibili = html.substring(inizioDisponibili);
        assertThat(seguiti).contains("Yoga").doesNotContain("Pilates").doesNotContain("Spinning");
        assertThat(disponibili).contains("Pilates").contains("Spinning").doesNotContain("Yoga");
        assertThat(html).doesNotContain("Zumba");
    }

    @Test
    @DisplayName("Senza corsi la pagina lo dice e non si rompe")
    void senzaCorsi() throws Exception {
        Utente mario = iscrittoAbbonato();

        mockMvc.perform(get(indirizzo(mario)).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Non è iscritto a nessun corso.")))
                .andExpect(content().string(containsString("Nessun altro corso a cui iscriverlo.")));
    }

    @Test
    @DisplayName("Per chi non ha il ruolo ISCRITTO la pagina spiega che servono ruolo e abbonamento, rimanda alla modifica e spegne «Iscrivi»")
    void paginaPerChiNonEIscritto() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        creaCorso("Yoga");

        mockMvc.perform(get(indirizzo(gino)).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("servono il ruolo ISCRITTO e un abbonamento attivo oggi")))
                .andExpect(content().string(containsString("/utenti/" + gino.getId() + "/modifica")))
                .andExpect(content().string(containsString("disabled")));
    }

    // ------------------------------------------------------------------ RICERCA, ORDINE, PAGINAZIONE

    @Test
    @DisplayName("La ricerca filtra i disponibili per nome, senza distinguere maiuscole e minuscole")
    void ricerca() throws Exception {
        Utente mario = iscrittoAbbonato();
        creaCorso("Yoga dolce");
        creaCorso("Spinning");

        mockMvc.perform(get(indirizzo(mario)).param("q", "YOGA").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Yoga dolce")))
                .andExpect(content().string(not(containsString("Spinning"))));
        mockMvc.perform(get(indirizzo(mario)).param("q", "nonesiste").with(comeAdmin()))
                .andExpect(content().string(containsString("Nessun corso corrisponde alla ricerca.")));
    }

    @Test
    @DisplayName("Nella ricerca «%» e «_» sono caratteri normali, non jolly")
    void ricercaConCaratteriSpeciali() throws Exception {
        Utente mario = iscrittoAbbonato();
        creaCorso("Sconto 50%");
        creaCorso("Sconto 500");
        creaCorso("Corso A_B");
        creaCorso("Corso AxB");

        mockMvc.perform(get(indirizzo(mario)).param("q", "50%").with(comeAdmin()))
                .andExpect(content().string(containsString("Sconto 50%")))
                .andExpect(content().string(not(containsString("Sconto 500"))));
        mockMvc.perform(get(indirizzo(mario)).param("q", "A_B").with(comeAdmin()))
                .andExpect(content().string(containsString("Corso A_B")))
                .andExpect(content().string(not(containsString("Corso AxB"))));
        // Un testo con SQL non è altro che un testo: nessun risultato e nessun errore.
        mockMvc.perform(get(indirizzo(mario)).param("q", "' or 1=1 --").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nessun corso corrisponde alla ricerca.")));
    }

    @Test
    @DisplayName("L'elenco dei disponibili è paginato: 25 corsi con size=10 sono 3 pagine, in ordine alfabetico")
    void paginazione() throws Exception {
        Utente mario = iscrittoAbbonato();
        for (int i = 10; i < 35; i++) {
            corsi.save(new Corso("Corso " + i, null, 60, Livello.PRINCIPIANTE));       // Corso 10 ... Corso 34
        }

        mockMvc.perform(get(indirizzo(mario)).param("size", "10").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Corso 10")))
                .andExpect(content().string(containsString("Corso 19")))
                .andExpect(content().string(not(containsString("Corso 20"))));
        mockMvc.perform(get(indirizzo(mario)).param("size", "10").param("page", "2").with(comeAdmin()))
                .andExpect(content().string(containsString("Corso 30")))
                .andExpect(content().string(containsString("Corso 34")))
                .andExpect(content().string(not(containsString("Corso 29"))));
    }

    @Test
    @DisplayName("Una pagina oltre l'ultima riporta all'ultima")
    void paginaOltreLUltima() throws Exception {
        Utente mario = iscrittoAbbonato();
        creaCorso("Yoga");

        mockMvc.perform(get(indirizzo(mario)).param("page", "7").with(comeAdmin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(indirizzo(mario)));
    }

    @Test
    @DisplayName("Parametri di pagina sbagliati sono un errore 400, non corretti in silenzio")
    void parametriSbagliati() throws Exception {
        Utente mario = iscrittoAbbonato();

        mockMvc.perform(get(indirizzo(mario)).param("size", "0").with(comeAdmin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(indirizzo(mario)).param("sort", "passwordHash,asc").with(comeAdmin())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ ISCRIVERE E DISISCRIVERE

    @Test
    @DisplayName("Iscrivere: messaggio verde, ritorno alla pagina, il corso passa tra quelli seguiti")
    void iscrivere() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(indirizzo(mario)))
                .andExpect(flash().attribute("messaggio", "Iscritto al corso Yoga"));

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
        String html = mockMvc.perform(get(indirizzo(mario)).with(comeAdmin())).andReturn().getResponse().getContentAsString();
        assertThat(html.substring(html.indexOf("Corsi a cui può essere iscritto"))).doesNotContain("Yoga");   // non è più offerto
    }

    @Test
    @DisplayName("Iscrivere due volte allo stesso corso è rifiutato con un messaggio, senza doppioni")
    void giaIscritto() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        iscrivereDirettamente(mario, yoga);

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errore", containsString("è già iscritto al corso Yoga")));

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
    }

    @Test
    @DisplayName("Disiscrivere: messaggio verde e il corso torna tra quelli disponibili; da un corso non seguito è rifiutato")
    void disiscrivere() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        iscrivereDirettamente(mario, yoga);

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/disiscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Iscrizione al corso Yoga tolta"));
        assertThat(nomiCorsiDi(mario)).isEmpty();

        mockMvc.perform(post(indirizzo(mario) + "/" + pilates.getId() + "/disiscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errore", containsString("non è iscritto al corso Pilates")));
    }

    @Test
    @DisplayName("Dopo l'iscrizione si torna alla pagina con lo stato della lista (dimensione e ricerca) com'era")
    void conservaLoStatoDellaLista() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga dolce");

        String ritorno = mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf())
                        .param("size", "10").param("q", "yoga"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(ritorno).startsWith(indirizzo(mario) + "?").contains("size=10").contains("q=yoga");
    }

    @Test
    @DisplayName("L'utente e il corso vengono dall'indirizzo: nessun campo del modulo può cambiarli")
    void sonoDallIndirizzo() throws Exception {
        Utente mario = iscrittoAbbonato();
        Utente altro = creaUtente("Altro", "Utente", "altro@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(altro, TipoAbbonamento.MENSILE, LocalDate.now());
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf())
                        .param("utenteId", altro.getId().toString()).param("corsoId", "999"))
                .andExpect(status().is3xxRedirection());

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
        assertThat(nomiCorsiDi(altro)).isEmpty();
    }

    // ------------------------------------------------------------------ L'EMAIL DI ISCRIZIONE

    @Test
    @DisplayName("Quando l'admin iscrive l'utente a un corso parte l'email di conferma all'utente, con il nome del corso")
    void emailDiIscrizione() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga dolce");

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(emailFinte.tutte()).hasSize(1);
        EmailFinte.Messaggio email = emailFinte.per("mario@example.com").get(0);
        assertThat(email.oggetto()).isEqualTo("Sei iscritto al corso Yoga dolce");
        assertThat(email.html()).contains("Yoga dolce").contains("/area/corsi");
        assertThat(email.testo()).contains("Yoga dolce");
    }

    @Test
    @DisplayName("Se l'iscrizione è rifiutata (senza abbonamento) o è una disiscrizione, nessuna email")
    void nessunaEmailSeNonIscritto() throws Exception {
        Utente senza = creaUtente("Luca", "Bianchi", "luca@example.com", ruolo("ISCRITTO"));
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        mockMvc.perform(post(indirizzo(senza) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()));
        emailFinte.svuota();

        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()));   // già iscritto
        mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/disiscrivi").with(comeAdmin()).with(csrf()));

        assertThat(emailFinte.tutte()).isEmpty();
        assertThat(nomiCorsiDi(senza)).isEmpty();
    }

    @Test
    @DisplayName("Se la posta non funziona l'iscrizione resta valida")
    void postaGuastaIscrizioneValida() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        emailFinte.faiFallire(true);
        try {
            mockMvc.perform(post(indirizzo(mario) + "/" + yoga.getId() + "/iscrivi").with(comeAdmin()).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        } finally {
            emailFinte.faiFallire(false);
        }

        assertThat(nomiCorsiDi(mario)).containsExactly("Yoga");
    }

    // ------------------------------------------------------------------ LA FINESTRA DI CONFERMA

    @Test
    @DisplayName("Ogni pulsante apre una finestra di conferma: il modulo ha data-conferma con la domanda, e la pagina contiene <dialog> e lo script")
    void finestraDiConferma() throws Exception {
        Utente mario = iscrittoAbbonato();
        creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        iscrivereDirettamente(mario, pilates);

        mockMvc.perform(get(indirizzo(mario)).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-conferma=\"Iscrivere Mario Rossi al corso Yoga?\"")))
                .andExpect(content().string(containsString("data-conferma=\"Togliere Mario Rossi dal corso Pilates?\"")))
                .andExpect(content().string(containsString("<dialog id=\"finestra-conferma\"")))
                .andExpect(content().string(containsString("/js/conferma.js")))
                .andExpect(content().string(containsString("Iscrivi...")))
                .andExpect(content().string(containsString("Disiscrivi...")));
    }

    @Test
    @DisplayName("Lo script della finestra di conferma è servito anche senza login")
    void loScriptEPubblico() throws Exception {
        mockMvc.perform(get("/js/conferma.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("finestra-conferma")))
                .andExpect(content().string(containsString("data-conferma")));
    }

    @Test
    @DisplayName("Un nome con caratteri speciali non rompe la domanda: Thymeleaf li protegge nell'attributo")
    void nomiConCaratteriSpeciali() throws Exception {
        Utente mario = creaUtente("Mario", "D'Angelo \"il Rosso\" <b>", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, LocalDate.now());
        creaCorso("Yoga");

        mockMvc.perform(get(indirizzo(mario)).with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<b>"))));           // il tag non viene interpretato come HTML
    }

    // ------------------------------------------------------------------ LISTA UTENTI

    @Test
    @DisplayName("Nella lista utenti il pulsante «Corsi (N)» porta alla pagina e i nomi dei corsi non sono più elencati nella riga")
    void ilPulsanteNellaLista() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        iscrivereDirettamente(mario, yoga, pilates);

        mockMvc.perform(get("/utenti").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/utenti/" + mario.getId() + "/corsi\"")))
                .andExpect(content().string(containsString("Corsi (2)")))
                .andExpect(content().string(not(containsString("Yoga"))))
                .andExpect(content().string(not(containsString("Pilates"))));
    }

    @Test
    @DisplayName("Anche la pagina dei corsi tiene i pulsanti in un <div> dentro la cella (nessun difetto di layout nelle tabelle)")
    void layoutDelleTabelle() throws Exception {
        Utente mario = iscrittoAbbonato();
        Corso yoga = creaCorso("Yoga");
        creaCorso("Pilates");
        iscrivereDirettamente(mario, yoga);

        mockMvc.perform(get(indirizzo(mario)).with(comeAdmin()))
                .andExpect(content().string(not(containsString("<td class=\"azioni\">"))))
                .andExpect(content().string(containsString("<td class=\"col-azioni\">")))
                .andExpect(content().string(containsString("<div class=\"azioni\">")));
    }
}
