/*
 * FILE: ApiAuthTest.java
 * A COSA SERVE: verifica la sicurezza della API con il flusso VERO dei token JWT: login, uso del token, e i casi in cui
 * il token deve essere RIFIUTATO (assente, scaduto, manomesso, firmato con un'altra chiave, ruolo insufficiente).
 * Qui NON si usa la scorciatoia jwt() dei test: i token sono quelli che emette davvero l'applicazione.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import it.palestra.model.Utente;
import it.palestra.service.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// OutputCaptureExtension "cattura" ciò che l'applicazione scrive nel terminale (il log) durante il test: così si può
// controllare che il MOTIVO di un rifiuto finisca nel log del server e NON nella risposta al client.
@ExtendWith(OutputCaptureExtension.class)
class ApiAuthTest extends BaseApiTest {

    @Autowired JwtService jwtService;
    @Autowired JwtEncoder jwtEncoder;     // il codificatore vero dell'applicazione (stessa chiave)
    @Autowired JwtDecoder jwtDecoder;     // il decodificatore vero: per leggere il contenuto dei token emessi

    // ----------------------------------------------------------------- METODI DI AIUTO

    // Fa il login con le credenziali indicate e restituisce il corpo JSON della risposta (come testo).
    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    // Esegue il login da admin e restituisce solo la stringa del token.
    private String tokenDaAdmin() throws Exception {
        return JsonPath.read(login(EMAIL_ADMIN, "admin"), "$.access_token");
    }

    // Costruisce un token con un contenuto deciso dal test e lo firma con l'encoder indicato.
    private String token(JwtEncoder encoder, String email, List<String> ruoli, Instant emesso, Instant scade) {
        JwtClaimsSet contenuto = JwtClaimsSet.builder().issuer("palestra").subject(email)
                .issuedAt(emesso).expiresAt(scade).claim("ruoli", ruoli).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), contenuto))
                .getTokenValue();
    }

    // La stessa richiesta GET a una risorsa protetta, con il token indicato nell'intestazione Authorization.
    private org.springframework.test.web.servlet.ResultActions chiediCorsiCon(String token) throws Exception {
        return mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // ------------------------------------------------------------------------------ LOGIN

    @Test
    @DisplayName("Login riuscito: risposta nel formato standard OAuth2 (access token 60 minuti, refresh token 7 giorni)")
    void loginRiuscito() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL_ADMIN + "\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))                  // app.jwt.durata-minuti = 60
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_expires_in").value(7 * 24 * 3600)); // app.jwt.refresh-durata-giorni = 7
    }

    @Test
    @DisplayName("Il token contiene l'email dell'utente, i suoi ruoli e una scadenza, e NON la password")
    void contenutoDelToken() throws Exception {
        String token = tokenDaAdmin();

        // Il decodificatore verifica la firma e ci restituisce il contenuto (i "claims").
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(EMAIL_ADMIN);
        assertThat(jwt.<List<String>>getClaim("ruoli")).containsExactly("ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        // Il contenuto di un JWT è leggibile da chiunque (è solo codificato): non deve mai esserci nulla di segreto,
        // né un campo "password" né l'hash bcrypt (che inizia con "$2a$"). Nel token c'è solo chi sei e cosa puoi fare.
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash");
        assertThat(jwt.getClaims().toString()).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("Con il token ottenuto dal login si accede alle risorse protette")
    void ilTokenFunziona() throws Exception {
        chiediCorsiCon(tokenDaAdmin()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Password sbagliata: 401 con un messaggio generico")
    void passwordSbagliata() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL_ADMIN + "\",\"password\":\"sbagliata\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.messaggio").value("Credenziali non valide"));
    }

    @Test
    @DisplayName("Email inesistente: risposta IDENTICA a quella della password sbagliata (non si scoprono le email)")
    void emailInesistente() throws Exception {
        String sbagliata = login(EMAIL_ADMIN, "sbagliata");
        String inesistente = login("nessuno@example.com", "qualunque");

        assertThat(inesistente).isEqualTo(sbagliata);
    }

    @Test
    @DisplayName("Un utente senza password (es. iscritto) non può ottenere un token")
    void utenteSenzaPassword() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mario@example.com\",\"password\":\"qualunque\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login senza email e password: 400 con i campi in errore")
    void loginCampiMancanti() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.email[0]").value("L'email è obbligatoria"))
                .andExpect(jsonPath("$.campi.password[0]").value("La password è obbligatoria"));
    }

    // ------------------------------------------------------ TOKEN ASSENTE, SBAGLIATO O INSUFFICIENTE

    @Test
    @DisplayName("Senza token: 401 in JSON (non un redirect alla pagina di login) con l'intestazione WWW-Authenticate")
    void senzaToken(CapturedOutput log) throws Exception {
        mockMvc.perform(get("/api/corsi"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errore").value("Non autenticato"));

        // Il log dice al programmatore che l'intestazione Authorization non c'era (causa tipica: "il token non viene inviato").
        assertThat(log.getAll()).contains("rifiutata con 401").contains("intestazione Authorization ASSENTE");
    }

    @Test
    @DisplayName("Un token inventato (non è nemmeno un JWT) è rifiutato")
    void tokenInventato() throws Exception {
        chiediCorsiCon("non-sono-un-token").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token SCADUTO è rifiutato, anche se la firma è giusta")
    void tokenScaduto(CapturedOutput log) throws Exception {
        // PREPARO: un token firmato con la chiave vera ma già scaduto da un'ora.
        Instant unOraFa = Instant.now().minusSeconds(3600);
        String scaduto = token(jwtEncoder, EMAIL_ADMIN, List.of("ADMIN"), unOraFa.minusSeconds(3600), unOraFa);

        chiediCorsiCon(scaduto).andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("expired"))));   // il client NON sa il motivo...

        // ...ma nel log del server sì (e il token in sé non viene mai scritto).
        assertThat(log.getAll()).contains("intestazione Authorization presente").contains("Jwt expired");
        assertThat(log.getAll()).doesNotContain(scaduto);
    }

    @Test
    @DisplayName("Un token MANOMESSO è rifiutato: cambiando anche un solo carattere la firma non torna")
    void tokenManomesso() throws Exception {
        String valido = tokenDaAdmin();
        // Si altera il PRIMO carattere della firma (la parte dopo l'ultimo punto). Non l'ultimo: in base64 l'ultimo
        // carattere porta alcuni bit "di riempimento" che il decodificatore ignora, e modificarlo a volte non cambierebbe
        // la firma (il test fallirebbe a caso, circa 1 volta su 16). Il primo carattere è tutto significativo: se lo
        // cambio, la firma è sicuramente diversa.
        int inizioFirma = valido.lastIndexOf('.') + 1;
        char primo = valido.charAt(inizioFirma);
        String manomesso = valido.substring(0, inizioFirma) + (primo == 'A' ? 'B' : 'A') + valido.substring(inizioFirma + 1);

        chiediCorsiCon(valido).andExpect(status().isOk());              // l'originale vale
        chiediCorsiCon(manomesso).andExpect(status().isUnauthorized()); // quello alterato no
    }

    @Test
    @DisplayName("Un token firmato con un'ALTRA chiave è rifiutato, anche se dice di essere un admin")
    void tokenFirmatoConAltraChiave(CapturedOutput log) throws Exception {
        // Chi non conosce la chiave segreta del server non può fabbricarsi un token valido: lo firma con la sua.
        var chiaveDelFalsario = new SecretKeySpec(
                "una-chiave-diversa-lunga-almeno-32-caratteri!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoderFalso = new NimbusJwtEncoder(new ImmutableSecret<>(chiaveDelFalsario));
        String falso = token(encoderFalso, EMAIL_ADMIN, List.of("ADMIN"), Instant.now(), Instant.now().plusSeconds(3600));

        chiediCorsiCon(falso).andExpect(status().isUnauthorized());
        assertThat(log.getAll()).contains("Invalid signature");
    }

    @Test
    @DisplayName("Un token valido ma SENZA il ruolo ADMIN riceve 403 (autenticato, non autorizzato)")
    void tokenSenzaRuoloAdmin(CapturedOutput log) throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));   // il token è valido solo se l'utente esiste
        String comeIscritto = jwtService.emetti(mario.getEmail(), List.of("ISCRITTO"), mario.getVersioneToken()).token();

        chiediCorsiCon(comeIscritto)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("Accesso negato"));
        assertThat(log.getAll()).contains("negata con 403");
    }

    @Test
    @DisplayName("Un'intestazione 'Bearer ' con il token VUOTO (variabile di Postman non risolta) è 401, e il log lo segnala")
    void tokenVuoto(CapturedOutput log) throws Exception {
        // Succede quando in Postman la variabile {{token}} è vuota: l'intestazione c'è, ma senza token dentro.
        mockMvc.perform(get("/api/corsi").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
        assertThat(log.getAll()).contains("intestazione Authorization presente").contains("malformed");
    }

    @Test
    @DisplayName("Il messaggio d'errore non rivela PERCHÉ il token è stato rifiutato")
    void messaggioGenerico() throws Exception {
        // Dire "scaduto" o "firma errata" aiuterebbe solo chi attacca: il messaggio è sempre lo stesso.
        chiediCorsiCon("x.y.z")
                .andExpect(content().string(not(containsString("signature"))))
                .andExpect(content().string(not(containsString("expired"))))
                .andExpect(jsonPath("$.messaggio").value(containsString("Token mancante, scaduto o non valido")));
    }

    // ----------------------------------------------------------- LE DUE CATENE SONO SEPARATE

    @Test
    @DisplayName("Il token della API non serve per le pagine: restano protette dal login a sessione")
    void ilTokenNonApreLePagine() throws Exception {
        mockMvc.perform(get("/utenti").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDaAdmin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("La pagina di login HTML continua a funzionare (la catena delle pagine non è cambiata)")
    void leNostrePagineSonoIntatte() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }
}
