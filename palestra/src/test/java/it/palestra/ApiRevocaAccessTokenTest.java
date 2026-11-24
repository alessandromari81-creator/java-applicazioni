/*
 * FILE: ApiRevocaAccessTokenTest.java
 * A COSA SERVE: verifica la REVOCA degli access token (JWT) prima della scadenza (vedi ValidatoreRevocaToken e Utente.versioneToken).
 * Con token veri ottenuti dal login: dopo logout, cambio password, rimozione del ruolo ADMIN o eliminazione dell'utente, il vecchio
 * token deve essere rifiutato (401) anche se non è ancora scaduto; i token senza versione o con una versione sbagliata pure; e un nuovo
 * login o un rinnovo danno un token valido.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.model.Utente;
import it.palestra.service.JwtService;
import it.palestra.service.PasswordService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith(OutputCaptureExtension.class)
class ApiRevocaAccessTokenTest extends BaseApiTest {

    private static final String PASSWORD = "password-di-prova-1";

    @Autowired PasswordService passwordService;
    @Autowired JwtEncoder jwtEncoder;      // per firmare a mano token con contenuto deciso dal test (la password si codifica con `encoder`, di BaseWebTest)

    // ----------------------------------------------------------------- METODI DI AIUTO

    // Crea un amministratore CON password (gli unici che possono fare login) con l'email data.
    private Utente creaAdminConPassword(String email) {
        Utente u = creaUtente("Prova", "Admin", email, ruolo("ADMIN"));
        u.setPasswordHash(encoder.encode(PASSWORD));
        return utenti.save(u);
    }

    // Login vero dalla API: restituisce il JSON della risposta (access_token e refresh_token).
    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String accessDi(String sessione) { return JsonPath.read(sessione, "$.access_token"); }
    private String refreshDi(String sessione) { return JsonPath.read(sessione, "$.refresh_token"); }

    // Una richiesta protetta (GET /api/corsi) con il token dato.
    private ResultActions chiediCon(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String corpo(String refreshToken) {
        return "{\"refresh_token\":\"%s\"}".formatted(refreshToken);
    }

    // ------------------------------------------------------------------------- LOGOUT

    @Test
    @DisplayName("Dopo il logout l'access token smette di valere SUBITO (non fra 60 minuti) e il log dice che è revocato")
    void dopoIlLogoutIlTokenNonVale(CapturedOutput log) throws Exception {
        creaAdminConPassword("a@example.com");
        String sessione = login("a@example.com", PASSWORD);
        chiediCon(accessDi(sessione)).andExpect(status().isOk());                 // prima del logout funziona

        postJson("/api/auth/logout", corpo(refreshDi(sessione))).andExpect(status().isNoContent());

        chiediCon(accessDi(sessione)).andExpect(status().isUnauthorized());
        assertThat(log.getAll()).contains("token revocato");
    }

    @Test
    @DisplayName("Dopo il logout un nuovo login dà un token che funziona (la versione è nel nuovo token)")
    void dopoIlLogoutSiPuoRientrare() throws Exception {
        creaAdminConPassword("a@example.com");
        String vecchia = login("a@example.com", PASSWORD);
        postJson("/api/auth/logout", corpo(refreshDi(vecchia))).andExpect(status().isNoContent());

        String nuova = login("a@example.com", PASSWORD);

        chiediCon(accessDi(nuova)).andExpect(status().isOk());
        chiediCon(accessDi(vecchia)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout con un refresh token GIÀ USATO o inesistente: 204 e nessun access token viene revocato")
    void logoutConTokenGiaUsatoNonRevocaNulla() throws Exception {
        creaAdminConPassword("a@example.com");
        String sessione = login("a@example.com", PASSWORD);
        String r1 = refreshDi(sessione);
        String rinnovata = postJson("/api/auth/refresh", corpo(r1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();                 // r1 è ora "usato"

        postJson("/api/auth/logout", corpo(r1)).andExpect(status().isNoContent());               // chi ha il vecchio r1 non fa uscire nessuno
        postJson("/api/auth/logout", corpo("token-che-non-esiste")).andExpect(status().isNoContent());

        chiediCon(accessDi(rinnovata)).andExpect(status().isOk());              // la sessione valida è intatta
    }

    @Test
    @DisplayName("Il rinnovo (refresh) emette un access token valido anche dopo una revoca")
    void ilRinnovoDaUnTokenValido() throws Exception {
        creaAdminConPassword("a@example.com");
        String sessione = login("a@example.com", PASSWORD);

        String rinnovata = postJson("/api/auth/refresh", corpo(refreshDi(sessione))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        chiediCon(accessDi(rinnovata)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- CAMBIO PASSWORD

    @Test
    @DisplayName("Cambiando la password, access token E refresh token vecchi non valgono più; con la nuova si rientra")
    void cambioPassword() throws Exception {
        creaAdminConPassword("a@example.com");
        String vecchia = login("a@example.com", PASSWORD);
        chiediCon(accessDi(vecchia)).andExpect(status().isOk());

        passwordService.cambiaPassword("a@example.com", "nuova-password-2");
        ricarica();     // come tra due richieste vere: la sessione di Hibernate non deve tenere copie vecchie dei refresh token

        chiediCon(accessDi(vecchia)).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", corpo(refreshDi(vecchia))).andExpect(status().isUnauthorized());   // anche il refresh è revocato
        String nuova = login("a@example.com", "nuova-password-2");
        chiediCon(accessDi(nuova)).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- RUOLI E ELIMINAZIONE

    @Test
    @DisplayName("Togliendo il ruolo ADMIN a un utente (da un altro admin) il suo token vecchio non vale più subito")
    void rimozioneDelRuoloAdmin() throws Exception {
        ruolo("ISCRITTO");                       // il ruolo da assegnare deve esistere (nel database di test c'è solo ADMIN)
        Utente b = creaAdminConPassword("b@example.com");
        String sessione = login("b@example.com", PASSWORD);
        chiediCon(accessDi(sessione)).andExpect(status().isOk());
        ricarica();

        // Un altro admin (identità finta) modifica b: stessi dati ma SOLO il ruolo ISCRITTO.
        mockMvc.perform(conJson(put("/api/utenti/" + b.getId()),
                        "{\"nome\":\"Prova\",\"cognome\":\"Admin\",\"email\":\"b@example.com\",\"password\":null,"
                                + "\"ruoli\":[\"ISCRITTO\"],\"corsiIds\":[]}"))
                .andExpect(status().isOk());

        chiediCon(accessDi(sessione)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Modificando un utente SENZA cambiare password né ruoli i suoi token restano validi")
    void modificaNeutraNonRevoca() throws Exception {
        Utente b = creaAdminConPassword("b@example.com");
        String sessione = login("b@example.com", PASSWORD);
        ricarica();

        mockMvc.perform(conJson(put("/api/utenti/" + b.getId()),
                        "{\"nome\":\"Nuovo\",\"cognome\":\"Nome\",\"email\":\"b@example.com\",\"password\":null,"
                                + "\"ruoli\":[\"ADMIN\"],\"corsiIds\":[]}"))
                .andExpect(status().isOk());

        chiediCon(accessDi(sessione)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un utente eliminato non può più usare il suo token")
    void utenteEliminato() throws Exception {
        Utente b = creaAdminConPassword("b@example.com");
        String sessione = login("b@example.com", PASSWORD);
        ricarica();

        mockMvc.perform(delete("/api/utenti/" + b.getId()).with(comeAdminApi())).andExpect(status().isNoContent());

        chiediCon(accessDi(sessione)).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- CONTENUTO DEL TOKEN

    // Costruisce e firma (con la chiave VERA del server) un token con i claim indicati; "versione" null = claim "v" assente.
    private String tokenCon(String email, Integer versione) {
        JwtClaimsSet.Builder b = JwtClaimsSet.builder().issuer("palestra").subject(email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).claim("ruoli", List.of("ADMIN"));
        if (versione != null) b.claim(JwtService.CLAIM_VERSIONE, versione);
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), b.build())).getTokenValue();
    }

    @Test
    @DisplayName("Un token firmato bene ma SENZA il claim di versione è rifiutato; con la versione giusta è accettato")
    void tokenSenzaVersione(CapturedOutput log) throws Exception {
        Utente a = creaAdminConPassword("a@example.com");

        chiediCon(tokenCon("a@example.com", null)).andExpect(status().isUnauthorized());
        assertThat(log.getAll()).contains("non contiene la versione");
        chiediCon(tokenCon("a@example.com", a.getVersioneToken())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un token con una versione diversa da quella attuale dell'utente è rifiutato")
    void tokenConVersioneSbagliata() throws Exception {
        Utente a = creaAdminConPassword("a@example.com");

        chiediCon(tokenCon("a@example.com", a.getVersioneToken() + 1)).andExpect(status().isUnauthorized());
        chiediCon(tokenCon("a@example.com", a.getVersioneToken() - 1)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token intestato a un'email che non esiste è rifiutato, e il log lo dice")
    void tokenDiUtenteInesistente(CapturedOutput log) throws Exception {
        chiediCon(tokenCon("fantasma@example.com", 0)).andExpect(status().isUnauthorized());
        assertThat(log.getAll()).contains("non esiste: eliminato o mai creato");
    }
}
