/*
 * FILE: AccountControllerTest.java
 * A COSA SERVE: verifica la pagina "Cambia password" (/account/password): che cambi davvero la password, che
 * rifiuti i dati sbagliati, e che si possa cambiare SOLO la propria.
 * Convenzione dei commenti: PREPARO (dati di partenza) / ESEGUO (richiesta) / VERIFICO (risposta e database).
 * Nel database di test l'admin iniziale (admin@palestra.local) ha la password "admin".
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import it.palestra.model.Utente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountControllerTest extends BaseWebTest {

    @Test
    @DisplayName("La pagina del cambio password è visibile a chi è loggato e mostra la sua email")
    void laPaginaSiVede() throws Exception {
        mockMvc.perform(get("/account/password").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(content().string(containsString(EMAIL_ADMIN)));
    }

    @Test
    @DisplayName("Senza login la pagina rimanda al login")
    void senzaLoginNonSiVede() throws Exception {
        mockMvc.perform(get("/account/password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Con i dati giusti la password cambia: la nuova vale, la vecchia no")
    void cambioPasswordRiuscito() throws Exception {
        // ESEGUO: attuale corretta ("admin"), nuova valida, conferma uguale.
        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("passwordAttuale", "admin")
                        .param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti"))
                .andExpect(flash().attribute("messaggio", "Password aggiornata"));

        // VERIFICO nel database: l'hash corrisponde alla nuova password e non più alla vecchia.
        ricarica();
        String hash = adminIniziale().getPasswordHash();
        assertThat(encoder.matches("nuova-password-1", hash)).isTrue();
        assertThat(encoder.matches("admin", hash)).isFalse();

        // VERIFICO col login vero: con la nuova si entra, con la vecchia no.
        mockMvc.perform(formLogin().user(EMAIL_ADMIN).password("nuova-password-1"))
                .andExpect(authenticated());
        mockMvc.perform(formLogin().user(EMAIL_ADMIN).password("admin"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Password attuale sbagliata: errore sul campo e password invariata")
    void passwordAttualeSbagliata() throws Exception {
        String hashPrima = adminIniziale().getPasswordHash();

        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("passwordAttuale", "sbagliata-123")
                        .param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-1"))
                .andExpect(status().isOk())                                   // niente redirect: si rimostra il form
                .andExpect(view().name("account/password"))
                .andExpect(model().attributeHasFieldErrorCode("cambiaPasswordForm", "passwordAttuale", "errata"))
                // Una password scritta in un form non deve MAI tornare nell'HTML della risposta.
                .andExpect(content().string(not(containsString("sbagliata-123"))))
                .andExpect(content().string(not(containsString("nuova-password-1"))));

        ricarica();
        assertThat(adminIniziale().getPasswordHash()).isEqualTo(hashPrima);   // nulla è cambiato
    }

    @Test
    @DisplayName("Nuova password troppo corta: rifiutata")
    void nuovaPasswordTroppoCorta() throws Exception {
        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("passwordAttuale", "admin")
                        .param("nuovaPassword", "corta")
                        .param("confermaPassword", "corta"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cambiaPasswordForm", "nuovaPassword", "non-valida"));
    }

    @Test
    @DisplayName("Nuova password uguale a quella attuale: rifiutata")
    void nuovaPasswordUgualeAllAttuale() throws Exception {
        // "admin" è lunga solo 5 caratteri, quindi prima la imposto più lunga e poi provo a "cambiarla" con la stessa.
        Utente admin = adminIniziale();
        admin.setPasswordHash(encoder.encode("password-attuale-1"));
        utenti.save(admin);

        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("passwordAttuale", "password-attuale-1")
                        .param("nuovaPassword", "password-attuale-1")
                        .param("confermaPassword", "password-attuale-1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cambiaPasswordForm", "nuovaPassword", "uguale"));
    }

    @Test
    @DisplayName("Conferma diversa dalla nuova password: rifiutata")
    void confermaDiversa() throws Exception {
        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("passwordAttuale", "admin")
                        .param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-2"))   // un errore di battitura
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cambiaPasswordForm", "confermaPassword", "diversa"));
    }

    @Test
    @DisplayName("Campi vuoti: tre errori, uno per campo")
    void campiVuoti() throws Exception {
        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("cambiaPasswordForm",
                        "passwordAttuale", "nuovaPassword", "confermaPassword"));
    }

    @Test
    @DisplayName("Si cambia solo la PROPRIA password: un'email nella richiesta viene ignorata")
    void nonSiCambiaLaPasswordDiUnAltro() throws Exception {
        // PREPARO: un secondo admin, Luca, con la sua password.
        Utente luca = creaUtente("Luca", "Neri", "luca@example.com", ruolo("ADMIN"));
        luca.setPasswordHash(encoder.encode("password-di-luca"));
        utenti.save(luca);

        // ESEGUO: l'admin loggato prova a "puntare" Luca aggiungendo parametri extra alla richiesta. Il controller
        // ricava l'utente SOLO dalla sessione (Principal), quindi questi parametri non hanno alcun effetto.
        mockMvc.perform(post("/account/password").with(comeAdmin()).with(csrf())
                        .param("email", "luca@example.com")
                        .param("id", luca.getId().toString())
                        .param("passwordAttuale", "admin")
                        .param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-1"))
                .andExpect(status().is3xxRedirection());

        // VERIFICO: è cambiata la password dell'admin loggato, NON quella di Luca.
        ricarica();
        assertThat(encoder.matches("nuova-password-1", adminIniziale().getPasswordHash())).isTrue();
        assertThat(encoder.matches("password-di-luca",
                utenti.findByEmailIgnoreCase("luca@example.com").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Un POST senza token CSRF è vietato")
    void senzaCsrfVietato() throws Exception {
        mockMvc.perform(post("/account/password").with(comeAdmin())
                        .param("passwordAttuale", "admin")
                        .param("nuovaPassword", "nuova-password-1")
                        .param("confermaPassword", "nuova-password-1"))
                .andExpect(status().isForbidden());
    }
}
