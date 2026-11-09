/*
 * FILE: ApiRefreshTokenTest.java
 * A COSA SERVE: verifica refresh token, rinnovo e logout della API:
 *   - il login restituisce un refresh token e il rinnovo ne dà uno NUOVO (rotazione: il vecchio non vale più);
 *   - il RIUSO di un token già usato è trattato come sospetto furto e chiude tutte le sessioni dell'utente;
 *   - il database conserva solo l'HASH del token, mai il token;
 *   - token sconosciuto, scaduto, revocato, o utente eliminato/senza password: 401;
 *   - il rinnovo emette un access token con i ruoli ATTUALI dell'utente.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.model.RefreshToken;
import it.palestra.model.Utente;
import it.palestra.repository.RefreshTokenRepository;
import it.palestra.service.RefreshTokenService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith(OutputCaptureExtension.class)
class ApiRefreshTokenTest extends BaseApiTest {

    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtDecoder jwtDecoder;

    // ----------------------------------------------------------------- METODI DI AIUTO

    // Esegue una POST JSON a un indirizzo della API e restituisce il risultato.
    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String corpoRefresh(String refreshToken) {
        return "{\"refresh_token\":\"%s\"}".formatted(refreshToken);
    }

    // Login: restituisce il corpo JSON della risposta (come testo), da cui si leggono i token con JsonPath.
    private String login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String loginAdmin() throws Exception {
        return login(EMAIL_ADMIN, "admin");
    }

    private String refreshDi(String rispostaJson) { return JsonPath.read(rispostaJson, "$.refresh_token"); }
    private String accessDi(String rispostaJson) { return JsonPath.read(rispostaJson, "$.access_token"); }

    private String rinnova(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", corpoRefresh(refreshToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // Crea un secondo admin con una password nota (l'admin iniziale resta, così i test sui ruoli non toccano "l'ultimo admin").
    private Utente creaAdminConPassword(String email, String password) {
        Utente u = creaUtente("Luca", "Neri", email, ruolo("ADMIN"));
        u.setPasswordHash(encoder.encode(password));
        return utenti.save(u);
    }

    // ------------------------------------------------------------------- RINNOVO E ROTAZIONE

    @Test
    @DisplayName("Il rinnovo restituisce una coppia di token NUOVA e l'access token ottenuto funziona")
    void rinnovoRestituisceNuovaCoppia() throws Exception {
        String prima = loginAdmin();

        String dopo = rinnova(refreshDi(prima));

        assertThat(refreshDi(dopo)).isNotEqualTo(refreshDi(prima));       // il refresh token è NUOVO (rotazione)
        assertThat(JsonPath.<Integer>read(dopo, "$.expires_in")).isEqualTo(3600);
        assertThat(JsonPath.<String>read(dopo, "$.token_type")).isEqualTo("Bearer");
        // Il nuovo access token è utilizzabile sulla API.
        mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessDi(dopo)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Il refresh token è usa-e-getta: dopo un rinnovo quello vecchio non vale più")
    void ilVecchioRefreshTokenNonVale() throws Exception {
        String refreshVecchio = refreshDi(loginAdmin());
        rinnova(refreshVecchio);

        postJson("/api/auth/refresh", corpoRefresh(refreshVecchio))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messaggio").value("Refresh token non valido o scaduto: rifai il login"));
    }

    @Test
    @DisplayName("RIUSO di un token già usato = sospetto furto: si revocano TUTTI i token dell'utente")
    void rilevamentoDelRiuso() throws Exception {
        String r1 = refreshDi(loginAdmin());
        String r2 = refreshDi(postJson("/api/auth/refresh", corpoRefresh(r1)).andReturn().getResponse().getContentAsString());

        // Qualcuno ripresenta r1 (già usato): rifiutato...
        postJson("/api/auth/refresh", corpoRefresh(r1)).andExpect(status().isUnauthorized());
        // ...e ANCHE r2, che era valido, ora non vale più: la sessione è stata chiusa per prudenza.
        postJson("/api/auth/refresh", corpoRefresh(r2)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Due accessi dello stesso utente (due dispositivi) hanno refresh token indipendenti")
    void sessioniIndipendenti() throws Exception {
        String dispositivoA = refreshDi(loginAdmin());
        String dispositivoB = refreshDi(loginAdmin());

        rinnova(dispositivoA);          // il dispositivo A rinnova...
        rinnova(dispositivoB);          // ...e il B funziona ancora, la rotazione è per singolo token
    }

    // ---------------------------------------------------------------------- TOKEN NON VALIDI

    @Test
    @DisplayName("Un refresh token sconosciuto o l'access token usato al suo posto: 401, e il token non finisce nel log")
    void tokenSconosciuto(CapturedOutput log) throws Exception {
        String accessToken = accessDi(loginAdmin());

        postJson("/api/auth/refresh", corpoRefresh("token-che-non-esiste")).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", corpoRefresh(accessToken)).andExpect(status().isUnauthorized());   // un JWT non è un refresh token

        assertThat(log.getAll()).contains("token sconosciuto");              // il motivo c'è, nel log del server
        assertThat(log.getAll()).doesNotContain("token-che-non-esiste").doesNotContain(accessToken);   // ma mai il token
    }

    @Test
    @DisplayName("Un refresh token SCADUTO è rifiutato")
    void tokenScaduto() throws Exception {
        Utente admin = adminIniziale();
        String chiaro = "refresh-scaduto-di-prova";
        Instant ora = Instant.now();
        // Salvo direttamente un token già scaduto (emesso 2 ore fa, scaduto da 1 ora).
        refreshTokenRepository.save(new RefreshToken(admin, RefreshTokenService.hash(chiaro),
                ora.minusSeconds(7200), ora.minusSeconds(3600)));

        postJson("/api/auth/refresh", corpoRefresh(chiaro)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Senza il campo refresh_token: 400 con il nome del campo come lo scrive il client")
    void campoMancante() throws Exception {
        postJson("/api/auth/refresh", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.refresh_token[0]").value("Il refresh_token è obbligatorio"));
        postJson("/api/auth/refresh", "{\"refresh_token\":\"   \"}").andExpect(status().isBadRequest());
        postJson("/api/auth/logout", "{}").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------ LOGOUT

    @Test
    @DisplayName("Logout: 204 e da quel momento il refresh token non vale più")
    void logout() throws Exception {
        String refresh = refreshDi(loginAdmin());

        postJson("/api/auth/logout", corpoRefresh(refresh)).andExpect(status().isNoContent());
        postJson("/api/auth/refresh", corpoRefresh(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Il logout risponde 204 anche per un token inesistente (così non si scopre quali esistono)")
    void logoutSilenzioso() throws Exception {
        postJson("/api/auth/logout", corpoRefresh("token-inventato")).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ COSA SI SALVA NEL DATABASE

    @Test
    @DisplayName("Nel database c'è solo l'HASH del refresh token, mai il token")
    void sidSalvaSoloLHash() throws Exception {
        String refresh = refreshDi(loginAdmin());
        ricarica();

        List<RefreshToken> salvati = refreshTokenRepository.findAll();
        assertThat(salvati).hasSize(1);
        assertThat(salvati.get(0).getTokenHash())
                .hasSize(64)                                         // SHA-256 in esadecimale
                .isNotEqualTo(refresh)                               // non è il token...
                .isEqualTo(RefreshTokenService.hash(refresh));       // ...ma il suo hash
        assertThat(refreshTokenRepository.findByTokenHash(refresh)).isEmpty();   // cercare col token in chiaro non trova nulla
        assertThat(salvati.get(0).getScadeIl()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600));   // dura circa 7 giorni
    }

    // ------------------------------------------------------ L'UTENTE CAMBIA DOPO IL LOGIN

    @Test
    @DisplayName("Un utente eliminato non può più rinnovare: i suoi token spariscono con lui")
    void utenteEliminato() throws Exception {
        Utente luca = creaAdminConPassword("luca@example.com", "password-di-luca");
        String refresh = refreshDi(login("luca@example.com", "password-di-luca"));
        ricarica();

        utenti.delete(utenti.findById(luca.getId()).orElseThrow());   // eliminazione (il database cancella anche i suoi token)
        ricarica();

        postJson("/api/auth/refresh", corpoRefresh(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Chi ha perso la password non può più rinnovare")
    void utenteSenzaPasswordNonRinnova() throws Exception {
        Utente luca = creaAdminConPassword("luca@example.com", "password-di-luca");
        String refresh = refreshDi(login("luca@example.com", "password-di-luca"));
        ricarica();

        Utente salvato = utenti.findById(luca.getId()).orElseThrow();
        salvato.setPasswordHash(null);                 // gli viene tolta la password
        utenti.save(salvato);
        ricarica();

        postJson("/api/auth/refresh", corpoRefresh(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Il rinnovo emette un access token con i ruoli ATTUALI: chi non è più ADMIN ottiene 403")
    void ilRinnovoUsaIRuoliAttuali() throws Exception {
        Utente luca = creaAdminConPassword("luca@example.com", "password-di-luca");
        String sessione = login("luca@example.com", "password-di-luca");
        String refresh = refreshDi(sessione);
        // Al login Luca era ADMIN: il suo access token lo dichiara.
        assertThat(jwtDecoder.decode(accessDi(sessione)).<List<String>>getClaim("ruoli")).containsExactly("ADMIN");
        ricarica();

        // Nel frattempo gli viene tolto il ruolo ADMIN (l'admin iniziale resta, quindi la regola dell'ultimo admin non scatta).
        Utente salvato = utenti.findById(luca.getId()).orElseThrow();
        salvato.setRuoli(new HashSet<>(Set.of(ruolo("ISCRITTO"))));
        utenti.save(salvato);
        ricarica();

        // Il rinnovo riflette la situazione di ADESSO...
        String nuova = rinnova(refresh);
        Jwt jwt = jwtDecoder.decode(accessDi(nuova));
        assertThat(jwt.<List<String>>getClaim("ruoli")).containsExactly("ISCRITTO");
        // ...e con quel token la API risponde 403: non è più un amministratore.
        mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessDi(nuova)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------ ACCESSO PUBBLICO

    @Test
    @DisplayName("Rinnovo e logout non richiedono l'access token (altrimenti non si potrebbe rinnovare quando è scaduto)")
    void rinnovoELogoutSonoPubblici() throws Exception {
        String refresh = refreshDi(loginAdmin());

        // Nessuna intestazione Authorization: il rinnovo funziona lo stesso.
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(corpoRefresh(refresh)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"errore\""))));
    }
}
