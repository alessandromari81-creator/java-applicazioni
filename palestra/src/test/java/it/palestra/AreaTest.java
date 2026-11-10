/*
 * FILE: AreaTest.java
 * A COSA SERVE: verifica l'AREA PERSONALE (/area) di iscritti e istruttori: chi può entrare e chi no, dove porta il login, e soprattutto che ciascuno
 * veda SOLO i propri dati (abbonamenti, corsi, e per l'istruttore solo gli iscritti dei SUOI corsi, solo con nome e cognome).
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Corso;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AreaTest extends BaseWebTest {

    private final LocalDate oggi = LocalDate.now();

    // Iscrive l'utente ai corsi indicati (lato proprietario della relazione).
    private void iscrivi(Utente u, Corso... corsiScelti) {
        u.setCorsi(new HashSet<>(Set.of(corsiScelti)));
        utenti.save(u);
    }

    // ------------------------------------------------------------------ ACCESSI PER RUOLO

    @Test
    @DisplayName("Senza login /area manda alla pagina di login")
    void anonimoVaAlLogin() throws Exception {
        mockMvc.perform(get("/area"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("Un iscritto e un istruttore entrano in /area; un amministratore SENZA quei ruoli no (403)")
    void accessoPerRuolo() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(get("/area").with(comeUtente("mario@example.com", "ISCRITTO"))).andExpect(status().isOk());
        mockMvc.perform(get("/area").with(comeUtente("gino@example.com", "ISTRUTTORE"))).andExpect(status().isOk());
        mockMvc.perform(get("/area").with(comeAdmin())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un iscritto NON può usare il pannello di amministrazione: utenti, corsi, abbonamenti, creazioni e Swagger danno 403")
    void ilPannelloResteRiservatoAdAdmin() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        var iscritto = comeUtente("mario@example.com", "ISCRITTO");

        for (String url : new String[] {"/utenti", "/utenti/nuovo", "/corsi", "/corsi/nuovo", "/abbonamenti", "/abbonamenti/nuovo", "/swagger-ui.html", "/v3/api-docs"}) {
            mockMvc.perform(get(url).with(iscritto)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/utenti/1/elimina").with(iscritto).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("La radice / manda l'amministratore al pannello e gli altri alla loro area")
    void laRadiceSmistaPerRuolo() throws Exception {
        mockMvc.perform(get("/").with(comeAdmin())).andExpect(redirectedUrl("/utenti"));
        mockMvc.perform(get("/").with(comeUtente("mario@example.com", "ISCRITTO"))).andExpect(redirectedUrl("/area"));
        mockMvc.perform(get("/").with(comeUtente("gino@example.com", "ISTRUTTORE"))).andExpect(redirectedUrl("/area"));
    }

    // -------------------------------------------------------------------- LOGIN

    @Test
    @DisplayName("Dopo il login un iscritto va in /area e un amministratore in /utenti")
    void loginPortaAllaPaginaGiusta() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "password-di-prova", ruolo("ISCRITTO"));

        mockMvc.perform(post("/login").with(csrf()).param("username", "mario@example.com").param("password", "password-di-prova"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/area"));
        mockMvc.perform(post("/login").with(csrf()).param("username", EMAIL_ADMIN).param("password", "admin"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/utenti"));
    }

    // ------------------------------------------------------------- DATI DELLA PERSONA

    @Test
    @DisplayName("Il riepilogo mostra nome, abbonamento e corsi della persona, con la barra dell'area (non quella del pannello)")
    void riepilogo() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        iscrivi(mario, yoga);
        creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);
        ricarica();

        mockMvc.perform(get("/area").with(comeUtente("mario@example.com", "ISCRITTO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ciao, Mario Rossi")))
                .andExpect(content().string(containsString("Yoga")))
                .andExpect(content().string(containsString("Annuale")))
                .andExpect(content().string(containsString("Attivo")))
                .andExpect(content().string(containsString("La mia area")))
                .andExpect(content().string(not(containsString("Pannello di controllo"))))
                .andExpect(content().string(containsString("Esci")));
    }

    @Test
    @DisplayName("Ciascuno vede SOLO i propri abbonamenti: gli altri iscritti non compaiono, né i loro tipi e prezzi")
    void soloIPropriAbbonamenti() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente laura = creaUtente("Laura", "Bianchi", "laura@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);          // 380,00 €
        creaAbbonamento(laura, TipoAbbonamento.MENSILE, oggi);          // 40,00 €
        ricarica();

        mockMvc.perform(get("/area/abbonamenti").with(comeUtente("mario@example.com", "ISCRITTO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annuale")))
                .andExpect(content().string(containsString("380,00")))
                .andExpect(content().string(not(containsString("Mensile"))))
                .andExpect(content().string(not(containsString("Bianchi"))))
                .andExpect(content().string(containsString("Risultati 1–1 di 1")));
        mockMvc.perform(get("/area/abbonamenti").with(comeUtente("laura@example.com", "ISCRITTO")))
                .andExpect(content().string(containsString("Mensile")))
                .andExpect(content().string(not(containsString("Annuale"))));
    }

    @Test
    @DisplayName("Lo storico è paginato come le altre liste e una pagina oltre l'ultima porta all'ultima")
    void storicoPaginato() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        for (int i = 0; i < 7; i++) creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusMonths(i));
        ricarica();
        var comeMario = comeUtente("mario@example.com", "ISCRITTO");

        mockMvc.perform(get("/area/abbonamenti").param("size", "3").param("page", "1").with(comeMario))
                .andExpect(content().string(containsString("Risultati 4–6 di 7")));
        mockMvc.perform(get("/area/abbonamenti").param("size", "3").param("page", "9").with(comeMario))
                .andExpect(redirectedUrl("/area/abbonamenti?page=2&size=3"));
    }

    @Test
    @DisplayName("Un iscritto senza abbonamenti vede un messaggio, non un errore; chi non è iscritto vede che il profilo non li prevede")
    void senzaAbbonamenti() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(get("/area").with(comeUtente("mario@example.com", "ISCRITTO")))
                .andExpect(content().string(containsString("Non hai ancora nessun abbonamento")));
        mockMvc.perform(get("/area").with(comeUtente("gino@example.com", "ISTRUTTORE")))
                .andExpect(content().string(containsString("non prevede abbonamenti")));
    }

    // ----------------------------------------------------------------- ISTRUTTORE

    @Test
    @DisplayName("L'istruttore vede gli iscritti dei SUOI corsi (solo cognome e nome), non quelli dei corsi di altri istruttori né le loro email")
    void istruttoreVedeSoloGliIscrittiDeiSuoiCorsi() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Utente anna = creaUtente("Anna", "Neri", "anna@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        yoga.setIstruttore(gino);
        corsi.save(yoga);
        Corso spinning = creaCorso("Spinning");
        spinning.setIstruttore(anna);
        corsi.save(spinning);
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente luca = creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));
        iscrivi(mario, yoga);
        iscrivi(luca, spinning);
        ricarica();

        mockMvc.perform(get("/area/corsi").with(comeUtente("gino@example.com", "ISTRUTTORE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Corsi che tengo")))
                .andExpect(content().string(containsString("Yoga")))
                .andExpect(content().string(containsString("Rossi Mario")))
                .andExpect(content().string(not(containsString("Gialli Luca"))))       // iscritto a un corso di un altro istruttore
                .andExpect(content().string(not(containsString("Spinning"))))
                .andExpect(content().string(not(containsString("mario@example.com")))) // niente email degli iscritti
                .andExpect(content().string(not(containsString("luca@example.com"))));
    }

    @Test
    @DisplayName("Chi non è istruttore non ha la sezione «Corsi che tengo»; un istruttore senza corsi lo vede scritto")
    void sezioneCorsiTenuti() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(get("/area/corsi").with(comeUtente("mario@example.com", "ISCRITTO")))
                .andExpect(content().string(not(containsString("Corsi che tengo"))));
        mockMvc.perform(get("/area/corsi").with(comeUtente("gino@example.com", "ISTRUTTORE")))
                .andExpect(content().string(containsString("Al momento non tieni nessun corso")));
    }

    // ------------------------------------------------------------ PROFILO E CONSENSI

    @Test
    @DisplayName("Il profilo mostra i dati personali, incluso l'indirizzo, e l'accettazione dell'informativa")
    void profilo() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        mario.setIndirizzo("Via Roma 1");
        mario.setCap("00100");
        mario.setCitta("Roma");
        mario.accettaPrivacy(java.time.Instant.parse("2026-11-10T09:30:00Z"), "2026-11");
        utenti.save(mario);
        ricarica();

        mockMvc.perform(get("/area/profilo").with(comeUtente("mario@example.com", "ISCRITTO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Via Roma 1, 00100 Roma")))
                .andExpect(content().string(containsString("Accettata il 10/11/2026")))
                .andExpect(content().string(containsString("/area/profilo/marketing")));
    }

    @Test
    @DisplayName("Il consenso marketing si dà e si ritira dal profilo; ridarlo non cambia la data del primo consenso")
    void consensoMarketing() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        var comeMario = comeUtente("mario@example.com", "ISCRITTO");

        mockMvc.perform(post("/area/profilo/marketing").with(comeMario).with(csrf()).param("consenso", "true"))
                .andExpect(redirectedUrl("/area/profilo"))
                .andExpect(flash().attribute("messaggio", "Consenso alle comunicazioni promozionali registrato"));
        ricarica();
        var primoConsenso = utenti.findById(mario.getId()).orElseThrow().getConsensoMarketingIl();
        assertThat(primoConsenso).isNotNull();

        mockMvc.perform(post("/area/profilo/marketing").with(comeMario).with(csrf()).param("consenso", "true"));
        ricarica();
        assertThat(utenti.findById(mario.getId()).orElseThrow().getConsensoMarketingIl()).isEqualTo(primoConsenso);

        // Casella non spuntata: il browser non invia il campo e il consenso viene ritirato.
        mockMvc.perform(post("/area/profilo/marketing").with(comeMario).with(csrf()))
                .andExpect(flash().attribute("messaggio", "Consenso alle comunicazioni promozionali ritirato"));
        ricarica();
        assertThat(utenti.findById(mario.getId()).orElseThrow().getConsensoMarketingIl()).isNull();
    }

    @Test
    @DisplayName("Il consenso marketing richiede il token CSRF (senza è 403) e si cambia solo il proprio")
    void consensoMarketingProtetto() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente laura = creaUtente("Laura", "Bianchi", "laura@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/area/profilo/marketing").with(comeUtente("mario@example.com", "ISCRITTO")).param("consenso", "true"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/area/profilo/marketing").with(comeUtente("mario@example.com", "ISCRITTO")).with(csrf())
                .param("consenso", "true").param("id", String.valueOf(laura.getId())).param("email", "laura@example.com"));
        ricarica();

        assertThat(utenti.findById(mario.getId()).orElseThrow().getConsensoMarketingIl()).isNotNull();
        assertThat(utenti.findById(laura.getId()).orElseThrow().getConsensoMarketingIl()).isNull();
    }

    // ---------------------------------------------------------------- CAMBIA PASSWORD

    @Test
    @DisplayName("Anche iscritti e istruttori cambiano la propria password, con la barra dell'area; dopo il cambio tornano in /area")
    void cambiaPasswordDelNonAmministratore() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        var comeMario = comeUtente("mario@example.com", "ISCRITTO");

        mockMvc.perform(get("/account/password").with(comeMario))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La mia area")));
        mockMvc.perform(post("/account/password").with(comeMario).with(csrf())
                        .param("passwordAttuale", "vecchia-password").param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-1"))
                .andExpect(redirectedUrl("/area"));
        ricarica();

        mockMvc.perform(post("/login").with(csrf()).param("username", "mario@example.com").param("password", "nuova-password-1"))
                .andExpect(redirectedUrl("/area"));
    }
}
