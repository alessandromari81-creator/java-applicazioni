/*
 * FILE: LimiteLoginTest.java
 * A COSA SERVE: verifica, con richieste vere, il limite ai tentativi di login sia della API (POST /api/auth/login: 429 con Retry-After)
 * sia del pannello (POST /login: redirect a /login?bloccato). Le soglie di prova sono quelle di application.properties (5 per
 * coppia IP+email, 20 per IP). I contatori si azzerano prima di ogni test (BaseWebTest).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class LimiteLoginTest extends BaseWebTest {

    // ------------------------------------------------------------------------- API

    private ResultActions loginApi(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .with(richiesta -> { richiesta.setRemoteAddr(ip); return richiesta; }));
    }

    @Test
    @DisplayName("API: 5 password sbagliate = 401; al sesto tentativo 429 con Retry-After, anche con la password GIUSTA")
    void apiBloccoDopoCinqueFallimenti() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginApi(EMAIL_ADMIN, "sbagliata", "9.9.9.9").andExpect(status().isUnauthorized());
        }

        loginApi(EMAIL_ADMIN, "admin", "9.9.9.9")                       // password giusta, ma il blocco viene prima
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errore").value("Troppi tentativi"));
        String attesa = loginApi(EMAIL_ADMIN, "admin", "9.9.9.9").andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(attesa)).isBetween(1L, 15L * 60 + 1);   // al massimo la durata della finestra
    }

    @Test
    @DisplayName("API: il blocco di un indirizzo non tocca un altro indirizzo, né un'altra email dallo stesso indirizzo")
    void apiBloccoMirato() throws Exception {
        for (int i = 0; i < 5; i++) loginApi(EMAIL_ADMIN, "sbagliata", "9.9.9.9");

        loginApi(EMAIL_ADMIN, "admin", "8.8.8.8").andExpect(status().isOk());                   // altro indirizzo: entra
        loginApi("altro@example.com", "x", "9.9.9.9").andExpect(status().isUnauthorized());    // altra email: 401 normale, non 429
    }

    @Test
    @DisplayName("API: un login riuscito azzera il contatore: 4 errori + 1 successo + 4 errori non bloccano")
    void apiIlSuccessoAzzera() throws Exception {
        for (int i = 0; i < 4; i++) loginApi(EMAIL_ADMIN, "sbagliata", "9.9.9.9");
        loginApi(EMAIL_ADMIN, "admin", "9.9.9.9").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) loginApi(EMAIL_ADMIN, "sbagliata", "9.9.9.9").andExpect(status().isUnauthorized());
        loginApi(EMAIL_ADMIN, "admin", "9.9.9.9").andExpect(status().isOk());                   // non ancora bloccato
    }

    @Test
    @DisplayName("API: l'email scritta con maiuscole e spazi conta come la stessa (non si aggira il limite)")
    void apiEmailNormalizzata() throws Exception {
        String[] varianti = {"ADMIN@palestra.local", " admin@palestra.local", "admin@PALESTRA.local", "Admin@palestra.local ", "admin@palestra.local"};
        for (String v : varianti) loginApi(v, "sbagliata", "9.9.9.9").andExpect(status().isUnauthorized());

        loginApi(EMAIL_ADMIN, "admin", "9.9.9.9").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("API: 20 email diverse dallo stesso indirizzo bloccano l'indirizzo, anche per le email nuove")
    void apiBloccoPerIndirizzo() throws Exception {
        for (int i = 0; i < 20; i++) loginApi("utente" + i + "@example.com", "x", "7.7.7.7").andExpect(status().isUnauthorized());

        loginApi("nuova@example.com", "x", "7.7.7.7").andExpect(status().isTooManyRequests());
        loginApi("nuova@example.com", "x", "6.6.6.6").andExpect(status().isUnauthorized());     // altro indirizzo
    }

    @Test
    @DisplayName("API: i dati non validi (400) non contano come tentativi falliti")
    void apiRichiestaMalformataNonConta() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
        loginApi(EMAIL_ADMIN, "admin", "127.0.0.1").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------- PANNELLO

    private MockHttpServletRequestBuilder invioForm(String email, String password, String ip) {
        return post("/login").with(csrf()).param("username", email).param("password", password)
                .with(richiesta -> { richiesta.setRemoteAddr(ip); return richiesta; });
    }

    @Test
    @DisplayName("Pannello: password sbagliata = /login?error; dopo 5 errori anche la password giusta porta a /login?bloccato")
    void pannelloBlocco() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(invioForm(EMAIL_ADMIN, "sbagliata", "9.9.9.9"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(invioForm(EMAIL_ADMIN, "admin", "9.9.9.9"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?bloccato"));
        // Da un altro indirizzo si entra regolarmente.
        mockMvc.perform(invioForm(EMAIL_ADMIN, "admin", "8.8.8.8"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/utenti"));
    }

    @Test
    @DisplayName("Pannello: un login riuscito azzera il contatore")
    void pannelloIlSuccessoAzzera() throws Exception {
        for (int i = 0; i < 4; i++) mockMvc.perform(invioForm(EMAIL_ADMIN, "sbagliata", "9.9.9.9"));
        mockMvc.perform(invioForm(EMAIL_ADMIN, "admin", "9.9.9.9")).andExpect(redirectedUrl("/utenti"));

        for (int i = 0; i < 4; i++) mockMvc.perform(invioForm(EMAIL_ADMIN, "sbagliata", "9.9.9.9")).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(invioForm(EMAIL_ADMIN, "admin", "9.9.9.9")).andExpect(redirectedUrl("/utenti"));
    }

    @Test
    @DisplayName("Pannello e API condividono i contatori: errori da una parte contano anche dall'altra")
    void contatoriCondivisi() throws Exception {
        for (int i = 0; i < 3; i++) mockMvc.perform(invioForm(EMAIL_ADMIN, "sbagliata", "9.9.9.9"));
        for (int i = 0; i < 2; i++) loginApi(EMAIL_ADMIN, "sbagliata", "9.9.9.9").andExpect(status().isUnauthorized());

        loginApi(EMAIL_ADMIN, "admin", "9.9.9.9").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("La pagina di login mostra l'avviso quando si arriva con ?bloccato")
    void paginaDiLoginMostraAvviso() throws Exception {
        mockMvc.perform(get("/login").param("bloccato", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Troppi tentativi di accesso falliti")));
        mockMvc.perform(get("/login")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Troppi tentativi"))));
    }
}
