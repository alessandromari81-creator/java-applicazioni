/*
 * FILE: ApiRefreshTokenTransazioniTest.java
 * A COSA SERVE: prova il rilevamento del RIUSO dei refresh token in condizioni REALI di transazione.
 *
 * PERCHÉ UNA CLASSE A PARTE: tutti gli altri test della API girano dentro UNA transazione unica (BaseWebTest ha
 * @Transactional) che viene annullata alla fine. In produzione invece OGNI richiesta HTTP ha la sua transazione, che si
 * conclude (commit) alla fine della richiesta. La differenza conta: quando un rinnovo con un token già usato lancia
 * l'eccezione (401), la revoca di tutti i token fatta prima deve restare SALVATA nonostante l'eccezione
 * (noRollbackFor in RefreshTokenService/SessioneService). In un test con transazione unica la revoca resterebbe visibile
 * comunque, e quel dettaglio non verrebbe mai verificato.
 * Qui NON c'è @Transactional: ogni richiesta fa commit davvero, come in produzione. Siccome i dati restano nel database
 * di test, @AfterEach cancella i refresh token creati.
 */
package it.palestra;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.repository.RefreshTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Stesse annotazioni di BaseWebTest MA senza @Transactional: ogni richiesta ha la propria transazione e fa commit.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRefreshTokenTransazioniTest {

    @Autowired MockMvc mockMvc;
    @Autowired RefreshTokenRepository refreshTokens;

    // Senza il rollback automatico dei test, ripuliamo a mano ciò che abbiamo creato.
    @AfterEach
    void pulizia() {
        refreshTokens.deleteAll();
    }

    private String postJson(String url, String json, int statoAtteso) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(statoAtteso)).andReturn().getResponse().getContentAsString();
    }

    private String corpo(String refreshToken) {
        return "{\"refresh_token\":\"%s\"}".formatted(refreshToken);
    }

    @Test
    @DisplayName("Il riuso di un refresh token revoca DAVVERO tutti i token dell'utente (la revoca resta salvata dopo il 401)")
    void laRevocaDopoIlRiusoRestaSalvata() throws Exception {
        // Login (admin iniziale: password "admin") e un rinnovo regolare: r1 viene usato e r2 è quello valido.
        String sessione = postJson("/api/auth/login", "{\"email\":\"" + BaseWebTest.EMAIL_ADMIN + "\",\"password\":\"admin\"}", 200);
        String r1 = JsonPath.read(sessione, "$.refresh_token");
        String r2 = JsonPath.read(postJson("/api/auth/refresh", corpo(r1), 200), "$.refresh_token");

        // Qualcuno ripresenta r1 (già usato): 401. Questa richiesta termina con un'eccezione, ma deve lasciare salvata la
        // revoca di tutti i token dell'utente, perché ogni richiesta ha la sua transazione e fa commit.
        postJson("/api/auth/refresh", corpo(r1), 401);

        // Controllo in una richiesta SEPARATA: r2, che era valido, ora deve essere stato revocato.
        postJson("/api/auth/refresh", corpo(r2), 401);
    }

    @Test
    @DisplayName("Il riuso di un refresh token revoca DAVVERO anche gli access token già emessi (e resta salvato dopo il 401)")
    void ilRiusoRevocaGliAccessToken() throws Exception {
        // Login e un rinnovo regolare: a1 è l'access token del login, a2 quello emesso dal rinnovo; r1 è già usato.
        String sessione = postJson("/api/auth/login", "{\"email\":\"" + BaseWebTest.EMAIL_ADMIN + "\",\"password\":\"admin\"}", 200);
        String a1 = JsonPath.read(sessione, "$.access_token");
        String r1 = JsonPath.read(sessione, "$.refresh_token");
        String a2 = JsonPath.read(postJson("/api/auth/refresh", corpo(r1), 200), "$.access_token");
        chiediCorsi(a1, 200);
        chiediCorsi(a2, 200);

        postJson("/api/auth/refresh", corpo(r1), 401);               // il riuso di r1: sospetto furto

        // Richieste SEPARATE (ognuna con il proprio commit): entrambi gli access token sono ora revocati...
        chiediCorsi(a1, 401);
        chiediCorsi(a2, 401);
        // ...ma un nuovo login funziona e dà un token valido.
        String nuova = postJson("/api/auth/login", "{\"email\":\"" + BaseWebTest.EMAIL_ADMIN + "\",\"password\":\"admin\"}", 200);
        chiediCorsi(JsonPath.read(nuova, "$.access_token"), 200);
    }

    private void chiediCorsi(String accessToken, int statoAtteso) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/corsi")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().is(statoAtteso));
    }
}
