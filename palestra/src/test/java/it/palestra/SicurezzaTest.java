/*
 * FILE: SicurezzaTest.java
 * A COSA SERVE: verifica chi può entrare e dove: login, ruoli, protezione delle pagine e del CSRF.
 * Ogni metodo con @Test è un test indipendente. Si leggono sempre in tre momenti:
 *   1) PREPARO  i dati di partenza,
 *   2) ESEGUO   una richiesta con mockMvc.perform(...),
 *   3) VERIFICO la risposta con .andExpect(...) (o con assertThat sui dati).
 * Se anche una sola verifica non è vera, il test fallisce e il messaggio dice quale.
 */
package it.palestra;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SicurezzaTest extends BaseWebTest {

    // @DisplayName è il titolo leggibile del test: compare nel report dei test al posto del nome del metodo.
    @Test
    @DisplayName("Senza login, una pagina protetta rimanda alla pagina di login")
    void senzaLoginSiVieneMandatiAlLogin() throws Exception {
        // ESEGUO: richiesta anonima (nessun utente loggato) a /utenti.
        mockMvc.perform(get("/utenti"))
                // VERIFICO: risposta 3xx (redirect) verso /login.
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("La pagina di login è pubblica")
    void laPaginaDiLoginEPubblica() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())              // 200: si vede anche senza essere loggati
                .andExpect(view().name("login"));        // è stato scelto il template templates/login.html
    }

    @Test
    @DisplayName("Un amministratore vede il pannello")
    void unAdminVedeIlPannello() throws Exception {
        // comeAdmin() simula un utente loggato con ruolo ADMIN (vedi BaseWebTest).
        mockMvc.perform(get("/utenti").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/lista"));
    }

    @Test
    @DisplayName("Chi è loggato ma non è ADMIN riceve 403 (accesso negato)")
    void unIscrittoNonPuoEntrareNelPannello() throws Exception {
        // PREPARO: un utente loggato col solo ruolo ISCRITTO. Chiamare roles("ISCRITTO") equivale all'autorità
        // ROLE_ISCRITTO, che non basta per la regola hasRole("ADMIN") di SecurityConfig.
        mockMvc.perform(get("/utenti").with(user("mario@example.com").roles("ISCRITTO")))
                .andExpect(status().isForbidden());      // 403, non un redirect: è loggato, ma non autorizzato
    }

    @Test
    @DisplayName("Il login vero (email + password dal database) funziona")
    void loginConCredenzialiGiuste() throws Exception {
        // formLogin() simula l'invio del form di login (POST /login con username e password, token CSRF compreso).
        // Qui NON si usa comeAdmin(): Spring Security cerca davvero l'utente nel database (H2 di test), dove
        // AdminIniziale ha creato admin@palestra.local con la password "admin" cifrata, e confronta l'hash bcrypt.
        mockMvc.perform(formLogin().user(EMAIL_ADMIN).password("admin"))
                .andExpect(authenticated().withUsername(EMAIL_ADMIN))   // risulta autenticato come quell'utente
                .andExpect(redirectedUrl("/utenti"));                    // e viene mandato al pannello
    }

    @Test
    @DisplayName("Password sbagliata: login rifiutato")
    void loginConPasswordSbagliata() throws Exception {
        mockMvc.perform(formLogin().user(EMAIL_ADMIN).password("sbagliata"))
                .andExpect(unauthenticated())                            // nessun utente autenticato
                .andExpect(redirectedUrl("/login?error"));               // si torna al login con ?error
    }

    @Test
    @DisplayName("Email inesistente: stesso errore di una password sbagliata")
    void loginConEmailInesistente() throws Exception {
        // L'errore è identico a quello della password sbagliata: così chi attacca non può scoprire quali email esistono.
        mockMvc.perform(formLogin().user("nessuno@example.com").password("admin"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Un utente senza password (es. iscritto) non può fare login")
    void unUtenteSenzaPasswordNonPuoFareLogin() throws Exception {
        // PREPARO: un iscritto salvato SENZA password (passwordHash = null).
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        // ESEGUO + VERIFICO: anche scrivendo qualunque password, UtenteUserDetailsService lo tratta come "non trovato".
        mockMvc.perform(formLogin().user("mario@example.com").password("qualunque"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Un POST senza token CSRF è vietato (anche da admin)")
    void unPostSenzaTokenCsrfEVietato() throws Exception {
        // Il CSRF è la protezione che impedisce a un altro sito di inviare form al posto tuo.
        // Qui facciamo un POST da admin ma SENZA .with(csrf()): Spring lo blocca con 403.
        mockMvc.perform(post("/utenti").with(comeAdmin()))
                .andExpect(status().isForbidden());
    }
}
