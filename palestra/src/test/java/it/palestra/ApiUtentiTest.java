/*
 * FILE: ApiUtentiTest.java
 * A COSA SERVE: verifica la API REST degli utenti (/api/utenti): lista, dettaglio, creazione, modifica, eliminazione e
 * soprattutto che la API rispetti le STESSE regole delle pagine (ultimo admin, istruttori, abbonamenti, password) e che
 * non faccia mai uscire l'hash della password.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.model.Corso;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiUtentiTest extends BaseApiTest {

    private final LocalDate oggi = LocalDate.now();

    // JSON di richiesta per un utente. "ruoli" e "corsi" sono già scritti come frammenti JSON (es. ["ISCRITTO"]).
    private String utenteJson(String email, String password, String ruoli, String corsiIds) {
        String pw = password == null ? "null" : "\"" + password + "\"";
        return """
                {"nome":"Mario","cognome":"Rossi","email":"%s","password":%s,"ruoli":%s,"corsiIds":%s}
                """.formatted(email, pw, ruoli, corsiIds);
    }

    // ------------------------------------------------------------------------ LETTURA

    @Test
    @DisplayName("GET /api/utenti: l'hash della password NON esce mai, c'è solo \"haPassword\"")
    void nonEsceLaPassword() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(get("/api/utenti").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2a$"))))           // né l'hash bcrypt
                .andExpect(jsonPath("$[?(@.email=='" + EMAIL_ADMIN + "')].haPassword").value(true))
                .andExpect(jsonPath("$[?(@.email=='mario@example.com')].haPassword").value(false));
    }

    @Test
    @DisplayName("GET /api/utenti/{id}: ruoli, corsi frequentati e corsi tenuti")
    void dettaglio() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"), ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        gino.setCorsi(new HashSet<>(Set.of(pilates)));          // frequenta Pilates...
        utenti.save(gino);
        yoga.setIstruttore(gino);                               // ...e tiene Yoga
        corsi.save(yoga);
        ricarica();

        mockMvc.perform(get("/api/utenti/" + gino.getId()).with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gino@example.com"))
                .andExpect(jsonPath("$.ruoli[0]").value("ISCRITTO"))        // ruoli in ordine alfabetico
                .andExpect(jsonPath("$.ruoli[1]").value("ISTRUTTORE"))
                .andExpect(jsonPath("$.corsi[0].nome").value("Pilates"))
                .andExpect(jsonPath("$.corsiInsegnati[0].nome").value("Yoga"));
    }

    @Test
    @DisplayName("Un utente inesistente: 404")
    void inesistente() throws Exception {
        mockMvc.perform(get("/api/utenti/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messaggio").value("Utente 999999 non trovato"));
    }

    @Test
    @DisplayName("Senza token: 401")
    void senzaToken() throws Exception {
        mockMvc.perform(get("/api/utenti")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------- CREAZIONE

    @Test
    @DisplayName("POST /api/utenti: crea un iscritto (201, ruoli per nome, nessuna password)")
    void creareUnIscritto() throws Exception {
        Corso yoga = creaCorso("Yoga");
        ruolo("ISCRITTO");

        mockMvc.perform(conJson(post("/api/utenti"),
                        utenteJson("mario@example.com", null, "[\"ISCRITTO\"]", "[" + yoga.getId() + "]")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/utenti/\\d+")))
                .andExpect(jsonPath("$.ruoli[0]").value("ISCRITTO"))
                .andExpect(jsonPath("$.haPassword").value(false))
                .andExpect(jsonPath("$.corsi[0].nome").value("Yoga"));
    }

    @Test
    @DisplayName("I nomi dei ruoli non distinguono maiuscole e minuscole")
    void ruoliCaseInsensitive() throws Exception {
        ruolo("ISCRITTO");

        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("mario@example.com", null, "[\"iscritto\"]", "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruoli[0]").value("ISCRITTO"));
    }

    @Test
    @DisplayName("Un admin creato dalla API può subito fare login con la sua password e ottenere un token")
    void adminCreatoPuoFareLogin() throws Exception {
        ruolo("ADMIN");

        mockMvc.perform(conJson(post("/api/utenti"),
                        utenteJson("luca@example.com", "password-di-luca", "[\"ADMIN\"]", "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.haPassword").value(true));

        // Integrazione: la password salvata (cifrata) permette di ottenere un token vero dal login della API.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"luca@example.com\",\"password\":\"password-di-luca\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("Un ruolo che non esiste: 400 con l'elenco dei valori ammessi")
    void ruoloSconosciuto() throws Exception {
        ruolo("ISCRITTO");

        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("mario@example.com", null, "[\"CAPO\"]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.ruoli[0]").value(containsString("Ruoli sconosciuti: [CAPO]")))
                .andExpect(jsonPath("$.campi.ruoli[0]").value(containsString("Valori ammessi")));
    }

    @Test
    @DisplayName("Campi obbligatori mancanti e email non valida: 400 con un messaggio per campo")
    void validazione() throws Exception {
        mockMvc.perform(conJson(post("/api/utenti"), "{\"email\":\"non-una-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.nome[0]").value("Il nome è obbligatorio"))
                .andExpect(jsonPath("$.campi.cognome[0]").value("Il cognome è obbligatorio"))
                .andExpect(jsonPath("$.campi.email[0]").value("Email non valida"));
    }

    @Test
    @DisplayName("Email già usata (anche con maiuscole diverse): 400 sul campo email")
    void emailDuplicata() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("MARIO@example.com", null, "[]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.email[0]").value("Email già registrata"));
    }

    @Test
    @DisplayName("Un ADMIN senza password, e una password troppo corta: 400 sul campo password")
    void regoleSullaPassword() throws Exception {
        ruolo("ADMIN");

        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("a@example.com", null, "[\"ADMIN\"]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.password[0]").value("La password è obbligatoria per gli amministratori"));

        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("a@example.com", "corta", "[\"ADMIN\"]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.password[0]").value("La password deve avere da 8 a 72 caratteri"));
    }

    @Test
    @DisplayName("Più errori insieme vengono segnalati TUTTI nella stessa risposta")
    void piuErroriInsieme() throws Exception {
        ruolo("ADMIN");
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        // Email già usata E ADMIN senza password: il client li vede entrambi senza dover riprovare due volte.
        mockMvc.perform(conJson(post("/api/utenti"), utenteJson("mario@example.com", null, "[\"ADMIN\"]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.email").exists())
                .andExpect(jsonPath("$.campi.password").exists());
    }

    @Test
    @DisplayName("Non si può impostare l'hash della password dal JSON: il campo passwordHash è ignorato")
    void passwordHashIgnorato() throws Exception {
        ruolo("ISCRITTO");

        mockMvc.perform(conJson(post("/api/utenti"),
                        "{\"nome\":\"M\",\"cognome\":\"R\",\"email\":\"m@example.com\",\"ruoli\":[\"ISCRITTO\"],"
                                + "\"passwordHash\":\"$2a$10$inventato\",\"id\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.haPassword").value(false))           // l'hash inviato non è stato usato
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999)));
    }

    // -------------------------------------------------------------------- MODIFICA

    @Test
    @DisplayName("PUT sostituisce ruoli e corsi con quelli inviati")
    void modificare() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");
        ruolo("ISTRUTTORE");

        mockMvc.perform(conJson(put("/api/utenti/" + mario.getId()),
                        utenteJson("mario@example.com", null, "[\"ISCRITTO\",\"ISTRUTTORE\"]", "[" + yoga.getId() + "]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruoli", hasSize(2)))
                .andExpect(jsonPath("$.corsi[0].nome").value("Yoga"));
    }

    @Test
    @DisplayName("Modificando senza inviare la password, quella esistente resta invariata")
    void passwordOmessaResta() throws Exception {
        Utente luca = creaUtente("Luca", "Neri", "luca@example.com", ruolo("ADMIN"));
        luca.setPasswordHash(encoder.encode("password-di-luca"));
        utenti.save(luca);

        mockMvc.perform(conJson(put("/api/utenti/" + luca.getId()),
                        utenteJson("luca@example.com", null, "[\"ADMIN\"]", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haPassword").value(true));

        ricarica();
        assertThat(encoder.matches("password-di-luca", utenti.findById(luca.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("PUT su un utente inesistente: 404")
    void modificareInesistente() throws Exception {
        mockMvc.perform(conJson(put("/api/utenti/999999"), utenteJson("x@example.com", null, "[]", "[]")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un istruttore non può essere iscritto al corso che tiene: 400 sul campo corsiIds")
    void istruttoreNonIscrittoAlProprioCorso() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        yoga.setIstruttore(gino);
        corsi.save(yoga);
        ricarica();

        mockMvc.perform(conJson(put("/api/utenti/" + gino.getId()),
                        utenteJson("gino@example.com", null, "[\"ISTRUTTORE\"]", "[" + yoga.getId() + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.corsiIds[0]").value(containsString("Yoga")));
    }

    // ------------------------------------------- LE REGOLE DI BUSINESS DIVENTANO 409

    @Test
    @DisplayName("L'ultimo admin non si elimina (409) e non perde il ruolo ADMIN (409)")
    void ultimoAdmin() throws Exception {
        Utente admin = adminIniziale();
        ruolo("ISCRITTO");

        mockMvc.perform(delete("/api/utenti/" + admin.getId()).with(comeAdminApi()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value("Impossibile eliminare l'ultimo amministratore"));

        mockMvc.perform(conJson(put("/api/utenti/" + admin.getId()),
                        utenteJson(EMAIL_ADMIN, null, "[\"ISCRITTO\"]", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value("Non puoi togliere il ruolo ADMIN all'ultimo amministratore"));
    }

    @Test
    @DisplayName("Un istruttore che tiene corsi non si elimina e non perde il ruolo: 409")
    void istruttoreConCorsi() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        yoga.setIstruttore(gino);
        corsi.save(yoga);
        ricarica();
        ruolo("ISCRITTO");

        mockMvc.perform(delete("/api/utenti/" + gino.getId()).with(comeAdminApi()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value(containsString("è istruttore di 1 corsi")));

        mockMvc.perform(conJson(put("/api/utenti/" + gino.getId()),
                        utenteJson("gino@example.com", null, "[\"ISCRITTO\"]", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value(containsString("Non puoi togliere il ruolo ISTRUTTORE")));
    }

    @Test
    @DisplayName("Un utente con abbonamenti non si elimina e non perde il ruolo ISCRITTO: 409")
    void utenteConAbbonamenti() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);
        ruolo("ISTRUTTORE");

        mockMvc.perform(delete("/api/utenti/" + mario.getId()).with(comeAdminApi()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value(containsString("1 abbonamenti registrati")));

        mockMvc.perform(conJson(put("/api/utenti/" + mario.getId()),
                        utenteJson("mario@example.com", null, "[\"ISTRUTTORE\"]", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messaggio").value(containsString("Non puoi togliere il ruolo ISCRITTO")));
    }

    // ----------------------------------------------------------------- ELIMINAZIONE

    @Test
    @DisplayName("DELETE /api/utenti/{id}: 204 e poi l'utente non esiste più")
    void eliminare() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(delete("/api/utenti/" + mario.getId()).with(comeAdminApi()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/utenti/" + mario.getId()).with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE di un utente inesistente: 404")
    void eliminareInesistente() throws Exception {
        mockMvc.perform(delete("/api/utenti/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }
}
