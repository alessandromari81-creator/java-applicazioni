/*
 * FILE: PasswordDimenticataTest.java
 * A COSA SERVE: verifica il recupero della password via email: «password dimenticata», il link per scegliere la password e l'invito dell'amministratore.
 * Controlla il flusso completo (richiesta, email, link, nuova password, login) e le difese: risposta identica per email esistente o no (nessuna
 * enumerazione), link monouso e a tempo, apertura del link che NON lo consuma, limite alle richieste, revoca dei token dopo il cambio, intestazione
 * Referrer-Policy. Le email sono registrate da EmailFinte (nessun server di posta vero).
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.model.TipoTokenPassword;
import it.palestra.model.TokenPassword;
import it.palestra.model.Utente;
import it.palestra.repository.TokenPasswordRepository;
import it.palestra.service.GeneratoreToken;
import it.palestra.service.TokenPasswordService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PasswordDimenticataTest extends BaseWebTest {

    @Autowired TokenPasswordService tokenService;
    @Autowired TokenPasswordRepository tokenRepo;

    // ------------------------------------------------------------------ METODI DI AIUTO

    private ResultActions richiedi(String email) throws Exception {
        return mockMvc.perform(post("/password-dimenticata").with(csrf()).param("email", email));
    }

    private ResultActions imposta(String token, String nuova, String conferma) throws Exception {
        return mockMvc.perform(post("/password/imposta").with(csrf())
                .param("token", token).param("nuovaPassword", nuova).param("confermaPassword", conferma));
    }

    private MockHttpServletRequestBuilder loginDi(String email, String password) {
        return post("/login").with(csrf()).param("username", email).param("password", password);
    }

    // Crea un utente e gli fa chiedere il reset: restituisce il token dell'email ricevuta.
    private String tokenDiReset(String email) throws Exception {
        richiedi(email).andExpect(status().is3xxRedirection());
        return emailFinte.ultima().token();
    }

    // ------------------------------------------------------------------- RICHIESTA

    @Test
    @DisplayName("Con un'email registrata parte UN'email con il link per scegliere la password; la risposta è la solita")
    void richiestaConEmailEsistente() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        richiedi("mario@example.com").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.tutte()).hasSize(1);
        var email = emailFinte.ultima();
        assertThat(email.destinatario()).isEqualTo("mario@example.com");
        assertThat(email.testo()).contains("Mario");
        assertThat(email.link()).startsWith("http://localhost:8080/password/imposta?token=");
        assertThat(email.html()).contains("Scegli una nuova password").contains(email.link());
    }

    @Test
    @DisplayName("Con un'email NON registrata la risposta è identica e non parte nessuna email (nessuna enumerazione degli utenti)")
    void richiestaConEmailInesistente() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        richiedi("nessuno@example.com").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.tutte()).isEmpty();
        String conEsistente = mockMvc.perform(get("/password-dimenticata").param("inviata", "")).andReturn().getResponse().getContentAsString();
        assertThat(conEsistente).contains("Se l'indirizzo è registrato");
    }

    @Test
    @DisplayName("Le maiuscole nell'email non contano: MARIO@EXAMPLE.COM trova mario@example.com")
    void emailSenzaDistinguereMaiuscole() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        richiedi("  MARIO@Example.com ").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
    }

    @Test
    @DisplayName("Un'email con il formato sbagliato rimostra il modulo con l'errore, senza inviare nulla")
    void formatoSbagliato() throws Exception {
        richiedi("non-e-una-email").andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("richiestaEmailForm", "email"));
        richiedi("").andExpect(status().isOk());

        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Due richieste ravvicinate: la seconda non rispedisce l'email (pausa), e il primo link resta valido")
    void pausaTraDueEmail() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        richiedi("mario@example.com");
        String primoToken = emailFinte.ultima().token();
        richiedi("mario@example.com").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.tutte()).hasSize(1);
        assertThat(tokenService.trovaValido(primoToken)).isPresent();
    }

    @Test
    @DisplayName("Se il server di posta non risponde la persona vede comunque la solita risposta (nessun errore, nessun dettaglio)")
    void postaNonRaggiungibile() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        emailFinte.faiFallire(true);

        richiedi("mario@example.com").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Limite alle richieste: dopo 20 richieste dallo stesso indirizzo non parte più nulla, ma la risposta resta uguale")
    void limiteAlleRichieste() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        for (int i = 0; i < 20; i++) richiedi("sconosciuto" + i + "@example.com");

        richiedi("mario@example.com").andExpect(redirectedUrl("/password-dimenticata?inviata"));

        assertThat(emailFinte.tutte()).isEmpty();                       // il limite per indirizzo è scattato: niente email, nessun segnale
        // Da un altro indirizzo la stessa richiesta funziona.
        mockMvc.perform(post("/password-dimenticata").with(csrf()).param("email", "mario@example.com")
                .with(r -> { r.setRemoteAddr("8.8.4.4"); return r; }));
        assertThat(emailFinte.tutte()).hasSize(1);
    }

    // ------------------------------------------------------------------ IL LINK

    @Test
    @DisplayName("Aprire il link mostra il modulo e NON lo consuma: si può riaprire (le anteprime della posta non lo bruciano)")
    void aprireIlLinkNonLoConsuma() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/password/imposta").param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Nuova password")))
                    .andExpect(content().string(containsString("value=\"" + token + "\"")));
        }
        assertThat(tokenService.trovaValido(token)).isPresent();
    }

    @Test
    @DisplayName("Un link inesistente, vuoto, manomesso o scaduto dà la stessa pagina «link non valido» con il collegamento per chiederne uno nuovo")
    void linkNonValido() throws Exception {
        Utente mario = creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String vero = tokenDiReset("mario@example.com");
        // Un token scaduto: stessa riga, scadenza nel passato.
        String scaduto = "token-scaduto-di-prova";
        tokenRepo.save(new TokenPassword(utenti.getReferenceById(mario.getId()), TipoTokenPassword.ATTIVAZIONE,
                GeneratoreToken.hash(scaduto), Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1))));

        for (String token : new String[] {"inesistente", vero + "x", scaduto}) {
            mockMvc.perform(get("/password/imposta").param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("non è valido oppure è scaduto")))
                    .andExpect(content().string(containsString("/password-dimenticata")))
                    .andExpect(content().string(not(containsString("Nuova password"))));
        }
        mockMvc.perform(get("/password/imposta")).andExpect(content().string(containsString("non è valido oppure è scaduto")));
    }

    @Test
    @DisplayName("Le pagine con il token inviano Referrer-Policy: no-referrer (il token è nell'indirizzo e non deve uscire con i clic)")
    void referrerPolicy() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");

        mockMvc.perform(get("/password/imposta").param("token", token))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("Scegliendo la nuova password: il login funziona con la nuova e non più con la vecchia; il link è monouso")
    void impostaLaPassword() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");

        imposta(token, "nuova-password-1", "nuova-password-1").andExpect(redirectedUrl("/login?passwordImpostata"));
        ricarica();

        mockMvc.perform(loginDi("mario@example.com", "nuova-password-1")).andExpect(redirectedUrl("/area"));
        mockMvc.perform(loginDi("mario@example.com", "vecchia-password")).andExpect(redirectedUrl("/login?error"));
        // Il link è stato consumato: un secondo uso, anche con la password giusta, non vale.
        imposta(token, "altra-password-2", "altra-password-2").andExpect(status().isOk())
                .andExpect(content().string(containsString("non è valido oppure è scaduto")));
        mockMvc.perform(loginDi("mario@example.com", "altra-password-2")).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Password troppo corta o ripetizione diversa: si rimostra il modulo con gli errori e il link resta valido")
    void erroriSullaPassword() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");

        imposta(token, "corta", "corta").andExpect(status().isOk())
                .andExpect(content().string(containsString("da 8 a 72 caratteri")));
        imposta(token, "password-lunga-1", "password-diversa-2").andExpect(status().isOk())
                .andExpect(content().string(containsString("Le due password non coincidono")));
        imposta(token, "", "").andExpect(status().isOk());

        assertThat(tokenService.trovaValido(token)).isPresent();
        ricarica();
        mockMvc.perform(loginDi("mario@example.com", "vecchia-password")).andExpect(redirectedUrl("/area"));   // la password non è cambiata
    }

    @Test
    @DisplayName("Nel modulo le password non vengono mai rimesse nei campi dopo un errore")
    void leControPasswordNonTornanoNellHtml() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");

        imposta(token, "password-lunga-1", "password-diversa-2")
                .andExpect(content().string(not(containsString("password-lunga-1"))))
                .andExpect(content().string(not(containsString("password-diversa-2"))));
    }

    @Test
    @DisplayName("Una nuova richiesta SOSTITUISCE il link precedente (il vecchio smette di valere); quando uno si usa spariscono gli altri link dell'utente")
    void unSoloLinkPerTipo() throws Exception {
        Utente mario = creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        String primo = tokenService.emetti(mario.getId(), TipoTokenPassword.RESET);
        String secondo = tokenService.emetti(mario.getId(), TipoTokenPassword.RESET);
        String attivazione = tokenService.emetti(mario.getId(), TipoTokenPassword.ATTIVAZIONE);
        ricarica();

        assertThat(tokenService.trovaValido(primo)).isEmpty();
        assertThat(tokenService.trovaValido(secondo)).isPresent();
        assertThat(tokenService.trovaValido(attivazione)).isPresent();         // un altro tipo non viene toccato

        imposta(secondo, "nuova-password-1", "nuova-password-1");
        ricarica();
        assertThat(tokenService.trovaValido(attivazione)).isEmpty();           // usato uno, spariscono anche gli altri
    }

    @Test
    @DisplayName("Durata dei link: 1 ora per la password dimenticata, 24 ore per l'invito")
    void durataDeiLink() {
        Utente mario = creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        String reset = tokenService.emetti(mario.getId(), TipoTokenPassword.RESET);
        String attivazione = tokenService.emetti(mario.getId(), TipoTokenPassword.ATTIVAZIONE);

        TokenPassword r = tokenService.trovaValido(reset).orElseThrow();
        TokenPassword a = tokenService.trovaValido(attivazione).orElseThrow();
        assertThat(Duration.between(r.getCreatoIl(), r.getScadeIl())).isEqualTo(Duration.ofMinutes(60));
        assertThat(Duration.between(a.getCreatoIl(), a.getScadeIl())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("Nel database c'è solo l'HASH del token, mai il token dell'email")
    void soloLHashNelDatabase() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));
        String token = tokenDiReset("mario@example.com");
        ricarica();

        assertThat(tokenRepo.findAll()).extracting(t -> t.getTokenHash()).doesNotContain(token)
                .contains(GeneratoreToken.hash(token));
    }

    // ------------------------------------------------------------ EFFETTI SULLA SICUREZZA

    @Test
    @DisplayName("Reimpostare la password revoca i token della API: l'access token e il refresh token vecchi smettono di valere")
    void reimpostareRevocaITokenDellaApi() throws Exception {
        Utente admin = creaUtenteConPassword("Anna", "Neri", "anna@example.com", "vecchia-password", ruolo("ADMIN"));
        String sessione = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"vecchia-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(sessione, "$.access_token");
        String refresh = JsonPath.read(sessione, "$.refresh_token");
        mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andExpect(status().isOk());

        String token = tokenDiReset("anna@example.com");
        imposta(token, "nuova-password-1", "nuova-password-1");
        ricarica();

        mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        assertThat(admin.getId()).isNotNull();
    }

    @Test
    @DisplayName("Chi ha un profilo SENZA password (creato dall'amministratore) può sceglierla da «password dimenticata» e poi accedere")
    void profiloSenzaPassword() throws Exception {
        creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));

        String token = tokenDiReset("luca@example.com");
        imposta(token, "prima-password-1", "prima-password-1").andExpect(redirectedUrl("/login?passwordImpostata"));
        ricarica();

        mockMvc.perform(loginDi("luca@example.com", "prima-password-1")).andExpect(redirectedUrl("/area"));
    }

    // ------------------------------------------------------------------ PAGINE PUBBLICHE

    @Test
    @DisplayName("Le pagine di recupero, di registrazione e la privacy sono raggiungibili senza login; la pagina di login ha i link")
    void paginePubbliche() throws Exception {
        for (String url : new String[] {"/password-dimenticata", "/registrazione", "/privacy", "/login"}) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("/password-dimenticata")))
                .andExpect(content().string(containsString("/registrazione")));
    }

    @Test
    @DisplayName("Dopo aver scelto la password la pagina di login mostra il messaggio di conferma")
    void loginMostraIlMessaggio() throws Exception {
        mockMvc.perform(get("/login").param("passwordImpostata", ""))
                .andExpect(content().string(containsString("Password impostata: ora puoi accedere")));
    }

    // ------------------------------------------------------------- INVITO DELL'AMMINISTRATORE

    @Test
    @DisplayName("L'amministratore invita un utente senza password: parte l'email di attivazione e, aperto il link, l'utente sceglie la password e accede")
    void invitoDellAmministratore() throws Exception {
        Utente luca = creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + luca.getId() + "/invito").with(comeAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/utenti"))
                .andExpect(flash().attribute("messaggio", "Invito inviato a luca@example.com"));

        var email = emailFinte.ultima();
        assertThat(email.destinatario()).isEqualTo("luca@example.com");
        assertThat(email.oggetto()).isEqualTo("Attiva il tuo account");
        imposta(email.token(), "prima-password-1", "prima-password-1").andExpect(redirectedUrl("/login?passwordImpostata"));
        ricarica();
        mockMvc.perform(loginDi("luca@example.com", "prima-password-1")).andExpect(redirectedUrl("/area"));
    }

    @Test
    @DisplayName("L'invito mantiene lo stato della lista (pagina, ordinamento, ricerca) nel ritorno")
    void invitoMantieneLoStato() throws Exception {
        Utente luca = creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + luca.getId() + "/invito").with(comeAdmin()).with(csrf())
                        .param("size", "10").param("sort", "email,desc").param("q", "luca"))
                .andExpect(redirectedUrl("/utenti?size=10&sort=email,desc&q=luca"));
    }

    @Test
    @DisplayName("Non si invita chi ha già una password (messaggio in rosso, nessuna email)")
    void nonSiInvitaChiHaGiaLaPassword() throws Exception {
        Utente mario = creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + mario.getId() + "/invito").with(comeAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/utenti"))
                .andExpect(flash().attributeExists("errore"));

        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Se l'email di invito non parte l'amministratore lo vede (a differenza di «password dimenticata»)")
    void invitoConPostaGuasta() throws Exception {
        Utente luca = creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));
        emailFinte.faiFallire(true);

        mockMvc.perform(post("/utenti/" + luca.getId() + "/invito").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("errore", containsString("non è partita")));
    }

    @Test
    @DisplayName("L'invito è riservato agli amministratori (un iscritto riceve 403) e richiede il token CSRF")
    void invitoRiservato() throws Exception {
        Utente luca = creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + luca.getId() + "/invito").with(comeUtente("luca@example.com", "ISCRITTO")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/utenti/" + luca.getId() + "/invito").with(comeAdmin())).andExpect(status().isForbidden());
        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Nella lista utenti il pulsante «Invita» compare solo per chi non ha la password")
    void pulsanteInvita() throws Exception {
        creaUtente("Luca", "Gialli", "luca@example.com", ruolo("ISCRITTO"));
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "vecchia-password", ruolo("ISCRITTO"));

        String html = mockMvc.perform(get("/utenti").with(comeAdmin())).andReturn().getResponse().getContentAsString();

        // Admin iniziale e Mario hanno la password: solo Luca ha il pulsante.
        assertThat(html.split("Invita</button>", -1).length - 1).isEqualTo(1);
    }
}
