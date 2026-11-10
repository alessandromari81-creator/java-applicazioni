/*
 * FILE: RegistrazioneTest.java
 * A COSA SERVE: verifica la registrazione libera con conferma via email: il modulo e le sue regole (privacy obbligatoria, CAP, trappola anti-bot), la
 * tabella delle registrazioni IN ATTESA (nessun utente prima della conferma, una sola riga per email, un nuovo invio sostituisce il precedente),
 * l'email con il link, la conferma con scelta della password (crea l'utente ISCRITTO con i consensi), i casi limite (email già registrata,
 * scheda creata dall'amministratore senza password, link scaduto o riusato) e la pulizia notturna. Le email sono registrate da EmailFinte.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Corso;
import it.palestra.model.RegistrazioneInAttesa;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import it.palestra.repository.RegistrazioneInAttesaRepository;
import it.palestra.service.PuliziaProgrammata;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RegistrazioneTest extends BaseWebTest {

    @Autowired RegistrazioneInAttesaRepository inAttesa;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuliziaProgrammata pulizia;

    // Nel database di test c'è solo ADMIN: la conferma assegna il ruolo ISCRITTO, che deve esistere.
    @BeforeEach
    void preparaIlRuolo() {
        ruolo("ISCRITTO");
    }

    // ------------------------------------------------------------------ METODI DI AIUTO

    // Il modulo compilato correttamente. "sovrascrivi" = coppie nome, valore che SOSTITUISCONO quelle di partenza (o ne aggiungono di nuove).
    private MockHttpServletRequestBuilder modulo(String email, String... sovrascrivi) {
        java.util.Map<String, String> campi = new java.util.LinkedHashMap<>();
        campi.put("nome", "Mario");
        campi.put("cognome", "Rossi");
        campi.put("email", email);
        campi.put("indirizzo", "Via Roma 1");
        campi.put("cap", "00100");
        campi.put("citta", "Roma");
        campi.put("accettoPrivacy", "true");
        for (int i = 0; i < sovrascrivi.length; i += 2) campi.put(sovrascrivi[i], sovrascrivi[i + 1]);
        MockHttpServletRequestBuilder richiesta = post("/registrazione").with(csrf());
        campi.forEach(richiesta::param);
        return richiesta;
    }

    private ResultActions registra(String email) throws Exception {
        return mockMvc.perform(modulo(email));
    }

    private ResultActions scegliPassword(String token, String password, String conferma) throws Exception {
        return mockMvc.perform(post("/registrazione/conferma").with(csrf())
                .param("token", token).param("nuovaPassword", password).param("confermaPassword", conferma));
    }

    // Registra e restituisce il token dell'email di conferma.
    private String registraEPrendiIlToken(String email) throws Exception {
        registra(email).andExpect(redirectedUrl("/registrazione?inviata"));
        return emailFinte.ultima().token();
    }

    private MockHttpServletRequestBuilder loginDi(String email, String password) {
        return post("/login").with(csrf()).param("username", email).param("password", password);
    }

    // Simula il passare del tempo sulla registrazione in attesa: sposta indietro i suoi orari.
    private void invecchia(String email, long minutiFaUltimoInvio, long scadeTraMinuti) {
        jdbc.update("update registrazione_in_attesa set ultimo_invio_il = ?, scade_il = ? where email = ?",
                OffsetDateTime.now().minusMinutes(minutiFaUltimoInvio), OffsetDateTime.now().plusMinutes(scadeTraMinuti), email);
        ricarica();
    }

    // ------------------------------------------------------------------------ IL MODULO

    @Test
    @DisplayName("Il modulo mostra tutti i campi, le due caselle (privacy obbligatoria, marketing facoltativo) e il link all'informativa")
    void ilModulo() throws Exception {
        mockMvc.perform(get("/registrazione"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"nome\"")))
                .andExpect(content().string(containsString("name=\"indirizzo\"")))
                .andExpect(content().string(containsString("name=\"cap\"")))
                .andExpect(content().string(containsString("name=\"citta\"")))
                .andExpect(content().string(containsString("name=\"accettoPrivacy\"")))
                .andExpect(content().string(containsString("name=\"consensoMarketing\"")))
                .andExpect(content().string(containsString("/privacy")))
                .andExpect(content().string(containsString("class=\"trappola\"")))
                .andExpect(content().string(not(containsString("name=\"password\""))));    // la password si sceglie dopo, dal link
    }

    @Test
    @DisplayName("Dati validi: risposta «controlla la email», UNA riga in attesa, NESSUN utente creato, una email con il link di conferma")
    void richiestaValida() throws Exception {
        long utentiPrima = utenti.count();

        registra("Mario@Example.COM ").andExpect(redirectedUrl("/registrazione?inviata"));
        ricarica();

        assertThat(utenti.count()).isEqualTo(utentiPrima);                                  // non esiste ancora nessun account
        assertThat(utenti.findByEmailIgnoreCase("mario@example.com")).isEmpty();
        RegistrazioneInAttesa r = inAttesa.findByEmail("mario@example.com").orElseThrow();  // email in minuscolo, senza spazi
        assertThat(r.getNome()).isEqualTo("Mario");
        assertThat(r.getCap()).isEqualTo("00100");
        assertThat(r.getPrivacyAccettataIl()).isNotNull();
        assertThat(r.getPrivacyVersione()).isEqualTo("2026-11");
        assertThat(r.getConsensoMarketingIl()).isNull();                                    // casella facoltativa non spuntata

        assertThat(emailFinte.tutte()).hasSize(1);
        var email = emailFinte.ultima();
        assertThat(email.destinatario()).isEqualTo("mario@example.com");
        assertThat(email.link()).startsWith("http://localhost:8080/registrazione/conferma?token=");
        assertThat(email.html()).contains("Conferma e scegli la password");
    }

    @Test
    @DisplayName("Dopo l'invio la pagina mostra il messaggio e NASCONDE il modulo")
    void messaggioDopoLInvio() throws Exception {
        mockMvc.perform(get("/registrazione").param("inviata", ""))
                .andExpect(content().string(containsString("Controlla la tua email")))
                .andExpect(content().string(not(containsString("name=\"cognome\""))));
    }

    @Test
    @DisplayName("Il consenso marketing, se spuntato, viene registrato con la data")
    void consensoMarketingNelModulo() throws Exception {
        mockMvc.perform(modulo("mario@example.com", "consensoMarketing", "true"));
        ricarica();

        assertThat(inAttesa.findByEmail("mario@example.com").orElseThrow().getConsensoMarketingIl()).isNotNull();
    }

    @Test
    @DisplayName("Senza accettare la privacy non si procede: errore sul campo, nessuna riga, nessuna email")
    void privacyObbligatoria() throws Exception {
        mockMvc.perform(post("/registrazione").with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "Via Roma 1").param("cap", "00100").param("citta", "Roma"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrazioneForm", "accettoPrivacy"))
                .andExpect(content().string(containsString("Per registrarti devi accettare")));

        assertThat(inAttesa.count()).isZero();
        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Tutti i campi sono obbligatori; email e CAP hanno un formato; i testi lunghi non danno 500")
    void validazione() throws Exception {
        mockMvc.perform(post("/registrazione").with(csrf()).param("accettoPrivacy", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrazioneForm", "nome", "cognome", "email", "indirizzo", "cap", "citta"));
        mockMvc.perform(modulo("non-una-email")).andExpect(model().attributeHasFieldErrors("registrazioneForm", "email"));
        mockMvc.perform(modulo("a@example.com", "cap", "1234")).andExpect(model().attributeHasFieldErrors("registrazioneForm", "cap"));
        mockMvc.perform(modulo("a@example.com", "cap", "1234a")).andExpect(model().attributeHasFieldErrors("registrazioneForm", "cap"));
        mockMvc.perform(modulo("a@example.com", "nome", "x".repeat(256)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrazioneForm", "nome"));

        assertThat(inAttesa.count()).isZero();
        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Campo trappola compilato (un programma automatico): stessa risposta, ma non si salva nulla e non parte nessuna email")
    void campoTrappola() throws Exception {
        mockMvc.perform(modulo("bot@example.com", "sito", "http://spam.example"))
                .andExpect(redirectedUrl("/registrazione?inviata"));

        assertThat(inAttesa.count()).isZero();
        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Il modulo richiede il token CSRF (senza è 403)")
    void richiedeCsrf() throws Exception {
        mockMvc.perform(post("/registrazione").param("nome", "Mario")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------- PIÙ INVII, EMAIL SBAGLIATE

    @Test
    @DisplayName("Rimandare il modulo subito dopo non rispedisce l'email (pausa) e lascia valido il primo link; la riga resta una sola")
    void reinvioRavvicinato() throws Exception {
        String primo = registraEPrendiIlToken("mario@example.com");

        registra("mario@example.com").andExpect(redirectedUrl("/registrazione?inviata"));
        ricarica();

        assertThat(emailFinte.tutte()).hasSize(1);
        assertThat(inAttesa.count()).isEqualTo(1);
        mockMvc.perform(get("/registrazione/conferma").param("token", primo)).andExpect(content().string(containsString("Nuova password")));
    }

    @Test
    @DisplayName("Rimandare il modulo subito dopo con un dato corretto (es. il cognome): niente nuova email e primo link valido, ma l'account nasce con i dati dell'ULTIMO invio")
    void correzioneRavvicinataVinceSuiDatiVecchi() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");                         // cognome "Rossi"

        mockMvc.perform(modulo("mario@example.com", "cognome", "Rossini", "citta", "Milano", "consensoMarketing", "true"));
        ricarica();

        assertThat(emailFinte.tutte()).hasSize(1);                                          // nessuna seconda email
        assertThat(inAttesa.count()).isEqualTo(1);
        RegistrazioneInAttesa r = inAttesa.findByEmail("mario@example.com").orElseThrow();
        assertThat(r.getCognome()).isEqualTo("Rossini");                                    // i dati sono quelli dell'ultimo invio
        assertThat(r.getCitta()).isEqualTo("Milano");
        assertThat(r.getConsensoMarketingIl()).isNotNull();

        scegliPassword(token, "password-lunga-1", "password-lunga-1").andExpect(redirectedUrl("/login?registrato"));   // il PRIMO link vale ancora
        ricarica();
        Utente u = utenti.findByEmailIgnoreCase("mario@example.com").orElseThrow();
        assertThat(u.getCognome()).isEqualTo("Rossini");                                    // niente cognome sbagliato
        assertThat(u.getCitta()).isEqualTo("Milano");
        assertThat(u.getConsensoMarketingIl()).isNotNull();
    }

    @Test
    @DisplayName("Dopo la conferma, il link di una registrazione precedente (con il cognome sbagliato) non vale più e non crea nessun altro utente")
    void ilVecchioLinkDopoLaConferma() throws Exception {
        String vecchio = registraEPrendiIlToken("mario@example.com");
        invecchia("mario@example.com", 10, 60);
        mockMvc.perform(modulo("mario@example.com", "cognome", "Rossini"));                // dopo la pausa: sostituisce e invalida il link vecchio
        String nuovo = emailFinte.ultima().token();
        scegliPassword(nuovo, "password-lunga-1", "password-lunga-1");
        ricarica();
        long utentiPrima = utenti.count();

        mockMvc.perform(get("/registrazione/conferma").param("token", vecchio)).andExpect(content().string(containsString("non è valido")));
        scegliPassword(vecchio, "altra-password-2", "altra-password-2").andExpect(content().string(containsString("non è valido")));
        ricarica();

        assertThat(utenti.count()).isEqualTo(utentiPrima);                                  // nessun secondo utente
        assertThat(utenti.findByEmailIgnoreCase("mario@example.com").orElseThrow().getCognome()).isEqualTo("Rossini");
        mockMvc.perform(loginDi("mario@example.com", "altra-password-2")).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Rimandare il modulo dopo la pausa SOSTITUISCE la registrazione: nuova email, vecchio link non più valido, dati aggiornati, ancora una sola riga")
    void reinvioDopoLaPausa() throws Exception {
        String primo = registraEPrendiIlToken("mario@example.com");
        invecchia("mario@example.com", 10, 60);

        mockMvc.perform(modulo("mario@example.com", "nome", "Marione", "citta", "Milano"));
        ricarica();

        assertThat(emailFinte.tutte()).hasSize(2);
        String secondo = emailFinte.ultima().token();
        assertThat(secondo).isNotEqualTo(primo);
        assertThat(inAttesa.count()).isEqualTo(1);
        RegistrazioneInAttesa r = inAttesa.findByEmail("mario@example.com").orElseThrow();
        assertThat(r.getNome()).isEqualTo("Marione");
        assertThat(r.getCitta()).isEqualTo("Milano");
        mockMvc.perform(get("/registrazione/conferma").param("token", primo)).andExpect(content().string(containsString("non è valido")));
        mockMvc.perform(get("/registrazione/conferma").param("token", secondo)).andExpect(content().string(containsString("Nuova password")));
    }

    @Test
    @DisplayName("Email scritta male e poi corretta: le due registrazioni sono separate, la sbagliata non blocca nessuno e quella giusta si conferma")
    void emailSbagliataEPoiGiusta() throws Exception {
        registra("mario@exmple.com");                     // errore di battitura
        registra("mario@example.com");
        ricarica();
        assertThat(inAttesa.count()).isEqualTo(2);
        String tokenGiusto = emailFinte.per("mario@example.com").get(0).token();

        scegliPassword(tokenGiusto, "password-lunga-1", "password-lunga-1").andExpect(redirectedUrl("/login?registrato"));
        ricarica();

        assertThat(utenti.findByEmailIgnoreCase("mario@example.com")).isPresent();
        assertThat(utenti.findByEmailIgnoreCase("mario@exmple.com")).isEmpty();         // quella sbagliata non è mai diventata un account
        assertThat(inAttesa.findByEmail("mario@exmple.com")).isPresent();                // resta in attesa fino alla scadenza
        assertThat(inAttesa.findByEmail("mario@example.com")).isEmpty();                 // quella confermata è stata consumata
    }

    // ------------------------------------------------------------------- IL LINK DI CONFERMA

    @Test
    @DisplayName("Aprire il link di conferma mostra il modulo della password e NON lo consuma (si può riaprire); con Referrer-Policy no-referrer")
    void aprireIlLink() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/registrazione/conferma").param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Scegli la password per completare la registrazione")))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }
        ricarica();
        assertThat(inAttesa.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Link inesistente, manomesso o assente: «link non valido» con il collegamento per ripetere la registrazione")
    void linkNonValido() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");

        for (String t : new String[] {"inesistente", token + "x"}) {
            mockMvc.perform(get("/registrazione/conferma").param("token", t))
                    .andExpect(content().string(containsString("non è valido oppure è scaduto")))
                    .andExpect(content().string(containsString("Ripeti la registrazione")));
        }
        mockMvc.perform(get("/registrazione/conferma")).andExpect(content().string(containsString("non è valido oppure è scaduto")));
    }

    @Test
    @DisplayName("Link scaduto: non vale, né per aprire il modulo né per confermare; la pulizia notturna cancella la riga e lascia quelle valide")
    void linkScaduto() throws Exception {
        String scaduto = registraEPrendiIlToken("scaduta@example.com");
        String valido = registraEPrendiIlToken("valida@example.com");
        invecchia("scaduta@example.com", 25 * 60, -1);

        mockMvc.perform(get("/registrazione/conferma").param("token", scaduto)).andExpect(content().string(containsString("non è valido")));
        scegliPassword(scaduto, "password-lunga-1", "password-lunga-1").andExpect(content().string(containsString("non è valido")));
        ricarica();
        assertThat(utenti.findByEmailIgnoreCase("scaduta@example.com")).isEmpty();

        pulizia.puliziaRefreshTokenScaduti();
        ricarica();
        assertThat(inAttesa.findByEmail("scaduta@example.com")).isEmpty();
        assertThat(inAttesa.findByEmail("valida@example.com")).isPresent();
        mockMvc.perform(get("/registrazione/conferma").param("token", valido)).andExpect(content().string(containsString("Nuova password")));
    }

    // ----------------------------------------------------------------------- LA CONFERMA

    @Test
    @DisplayName("Scegliendo la password si crea l'utente ISCRITTO con indirizzo e consensi, la riga in attesa sparisce e si può accedere (all'area)")
    void conferma() throws Exception {
        mockMvc.perform(modulo("mario@example.com", "consensoMarketing", "true"));
        String token = emailFinte.ultima().token();

        scegliPassword(token, "password-lunga-1", "password-lunga-1").andExpect(redirectedUrl("/login?registrato"));
        ricarica();

        Utente u = utenti.findWithRelazioniByEmailIgnoreCase("mario@example.com").orElseThrow();
        assertThat(u.getNome()).isEqualTo("Mario");
        assertThat(u.getCognome()).isEqualTo("Rossi");
        assertThat(u.getIndirizzo()).isEqualTo("Via Roma 1");
        assertThat(u.getCap()).isEqualTo("00100");
        assertThat(u.getCitta()).isEqualTo("Roma");
        assertThat(u.isIscritto()).isTrue();
        assertThat(u.isAdmin()).isFalse();
        assertThat(u.isConPassword()).isTrue();
        assertThat(u.getPasswordHash()).doesNotContain("password-lunga-1");        // solo l'hash
        assertThat(u.getPrivacyAccettataIl()).isNotNull();
        assertThat(u.getPrivacyVersione()).isEqualTo("2026-11");
        assertThat(u.getConsensoMarketingIl()).isNotNull();
        assertThat(inAttesa.count()).isZero();
        mockMvc.perform(loginDi("mario@example.com", "password-lunga-1")).andExpect(redirectedUrl("/area"));
    }

    @Test
    @DisplayName("Senza il consenso marketing l'utente creato non ha consenso; il link è monouso (il secondo uso non vale)")
    void confermaSenzaMarketingEMonouso() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");

        scegliPassword(token, "password-lunga-1", "password-lunga-1");
        ricarica();
        assertThat(utenti.findByEmailIgnoreCase("mario@example.com").orElseThrow().getConsensoMarketingIl()).isNull();

        scegliPassword(token, "altra-password-2", "altra-password-2").andExpect(content().string(containsString("non è valido")));
        ricarica();
        mockMvc.perform(loginDi("mario@example.com", "altra-password-2")).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Password troppo corta o diversa: si rimostra il modulo con gli errori; il link resta valido e nessun utente viene creato")
    void erroriSullaPassword() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");

        scegliPassword(token, "corta", "corta").andExpect(content().string(containsString("da 8 a 72 caratteri")));
        scegliPassword(token, "password-lunga-1", "diversa-password-2").andExpect(content().string(containsString("non coincidono")));
        ricarica();

        assertThat(utenti.findByEmailIgnoreCase("mario@example.com")).isEmpty();
        assertThat(inAttesa.count()).isEqualTo(1);
        scegliPassword(token, "password-lunga-1", "password-lunga-1").andExpect(redirectedUrl("/login?registrato"));
    }

    @Test
    @DisplayName("Chi si è registrato ma non ha confermato NON compare nell'elenco utenti, né nella pagina né nella API")
    void inAttesaNonCompareNegliUtenti() throws Exception {
        registra("fantasma@example.com");
        ricarica();

        mockMvc.perform(get("/utenti").with(comeAdmin())).andExpect(content().string(not(containsString("fantasma@example.com"))));
        mockMvc.perform(get("/api/utenti").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.email=='fantasma@example.com')]").isEmpty());
    }

    // ----------------------------------------------------------------- EMAIL GIÀ PRESENTE

    @Test
    @DisplayName("Email di un account che ha già la password: nessuna riga in attesa, nessuna conferma; arriva un avviso «hai già un account»; risposta identica")
    void accountGiaEsistente() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "password-di-prova", ruolo("ISCRITTO"));

        registra("MARIO@example.com").andExpect(redirectedUrl("/registrazione?inviata"));
        ricarica();

        assertThat(inAttesa.count()).isZero();
        assertThat(emailFinte.tutte()).hasSize(1);
        var email = emailFinte.ultima();
        assertThat(email.oggetto()).isEqualTo("Hai già un account");
        assertThat(email.link()).isEqualTo("http://localhost:8080/password-dimenticata");
        assertThat(email.testo()).doesNotContain("token=");
    }

    @Test
    @DisplayName("Scheda creata dall'amministratore SENZA password: la registrazione la ATTIVA (password e consensi), senza cambiare nome e cognome e senza perdere corsi e abbonamenti")
    void attivaLaSchedaDellAmministratore() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Utente scheda = creaUtente("Marietto", "Rossetti", "mario@example.com", ruolo("ISCRITTO"));
        scheda.setCorsi(new HashSet<>(Set.of(yoga)));
        utenti.save(scheda);
        creaAbbonamento(scheda, TipoAbbonamento.MENSILE, LocalDate.now());
        ricarica();
        long utentiPrima = utenti.count();

        String token = registraEPrendiIlToken("mario@example.com");           // nel modulo: nome "Mario", cognome "Rossi", Roma
        scegliPassword(token, "password-lunga-1", "password-lunga-1").andExpect(redirectedUrl("/login?passwordImpostata"));
        ricarica();

        assertThat(utenti.count()).isEqualTo(utentiPrima);                      // nessun doppione
        Utente u = utenti.findWithRelazioniByEmailIgnoreCase("mario@example.com").orElseThrow();
        assertThat(u.getId()).isEqualTo(scheda.getId());
        assertThat(u.getNome()).isEqualTo("Marietto");                          // i dati dell'amministratore restano
        assertThat(u.getCognome()).isEqualTo("Rossetti");
        assertThat(u.getIndirizzo()).isEqualTo("Via Roma 1");                   // mancava: si completa
        assertThat(u.getPrivacyAccettataIl()).isNotNull();
        assertThat(u.getCorsi()).extracting(c -> c.getNome()).containsExactly("Yoga");
        assertThat(abbonamenti.countByUtenteId(u.getId())).isEqualTo(1);
        mockMvc.perform(loginDi("mario@example.com", "password-lunga-1")).andExpect(redirectedUrl("/area"));
    }

    @Test
    @DisplayName("Se l'indirizzo era già compilato dall'amministratore, la registrazione non lo sovrascrive")
    void nonSovrascriveLIndirizzo() throws Exception {
        Utente scheda = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        scheda.setIndirizzo("Via Esistente 9");
        scheda.setCap("20100");
        scheda.setCitta("Milano");
        utenti.save(scheda);
        ricarica();

        String token = registraEPrendiIlToken("mario@example.com");
        scegliPassword(token, "password-lunga-1", "password-lunga-1");
        ricarica();

        Utente u = utenti.findByEmailIgnoreCase("mario@example.com").orElseThrow();
        assertThat(u.getIndirizzo()).isEqualTo("Via Esistente 9");
        assertThat(u.getCitta()).isEqualTo("Milano");
    }

    @Test
    @DisplayName("Se tra la richiesta e la conferma qualcuno ha già un account con quella email e password: nessuna modifica, messaggio «esiste già», e la riga si cancella")
    void accountCreatoNelFrattempo() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");
        Utente esistente = creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "password-originale", ruolo("ISCRITTO"));
        ricarica();

        scegliPassword(token, "password-nuova-1", "password-nuova-1").andExpect(redirectedUrl("/login?giaRegistrato"));
        ricarica();

        assertThat(inAttesa.count()).isZero();
        mockMvc.perform(loginDi("mario@example.com", "password-originale")).andExpect(redirectedUrl("/area"));
        mockMvc.perform(loginDi("mario@example.com", "password-nuova-1")).andExpect(redirectedUrl("/login?error"));
        assertThat(esistente.getId()).isNotNull();
    }

    @Test
    @DisplayName("La pagina di login mostra i messaggi dopo la registrazione e se l'account esisteva già")
    void messaggiNelLogin() throws Exception {
        mockMvc.perform(get("/login").param("registrato", "")).andExpect(content().string(containsString("Registrazione completata")));
        mockMvc.perform(get("/login").param("giaRegistrato", "")).andExpect(content().string(containsString("Esiste già un account")));
    }

    // -------------------------------------------------------------------- LIMITI E POSTA

    @Test
    @DisplayName("Limite alle richieste: dopo 20 registrazioni dallo stesso indirizzo non parte più nessuna email, ma la risposta resta la stessa")
    void limite() throws Exception {
        for (int i = 0; i < 20; i++) registra("utente" + i + "@example.com");
        emailFinte.svuota();

        registra("ventunesimo@example.com").andExpect(redirectedUrl("/registrazione?inviata"));
        ricarica();

        assertThat(emailFinte.tutte()).isEmpty();
        assertThat(inAttesa.findByEmail("ventunesimo@example.com")).isEmpty();
    }

    @Test
    @DisplayName("Se il server di posta non risponde la persona vede comunque la solita risposta (la riga resta, la richiesta si può ripetere)")
    void postaNonRaggiungibile() throws Exception {
        emailFinte.faiFallire(true);

        registra("mario@example.com").andExpect(redirectedUrl("/registrazione?inviata"));

        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Nel database c'è solo l'HASH del token del link, mai il token")
    void soloLHash() throws Exception {
        String token = registraEPrendiIlToken("mario@example.com");
        ricarica();

        RegistrazioneInAttesa r = inAttesa.findByEmail("mario@example.com").orElseThrow();
        assertThat(r.getTokenHash()).isNotEqualTo(token).hasSize(64);
    }

    @Test
    @DisplayName("L'informativa privacy è pubblica e mostra la versione che chi si registra accetta")
    void paginaPrivacy() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informativa sulla privacy")))
                .andExpect(content().string(containsString("2026-11")));
    }
}
