/*
 * FILE: ApiCorsiTest.java
 * A COSA SERVE: verifica la API REST dei corsi (/api/corsi): lista, dettaglio, creazione, modifica, eliminazione, gli
 * errori in JSON con il codice giusto (400, 404, 409) e le regole sull'istruttore.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO. jsonPath("$.nome") legge il campo "nome" del JSON ricevuto.
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
import it.palestra.model.Utente;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiCorsiTest extends BaseApiTest {

    // Un JSON di richiesta valido per un corso, con l'istruttore facoltativo (null = nessuno).
    private String corsoJson(String nome, Long istruttoreId) {
        return """
                {"nome":"%s","descrizione":"Descrizione","durataMinuti":55,"livello":"INTERMEDIO","istruttoreId":%s}
                """.formatted(nome, istruttoreId);
    }

    // ------------------------------------------------------------------------ LETTURA

    @Test
    @DisplayName("GET /api/corsi: una PAGINA di corsi in JSON, in ordine alfabetico, con i dati per navigare")
    void elenco() throws Exception {
        creaCorso("Yoga");
        creaCorso("Pilates");

        mockMvc.perform(get("/api/corsi").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))             // metadati della pagina
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.content[0].nome").value("Pilates"))     // ordine alfabetico: Pilates prima di Yoga
                .andExpect(jsonPath("$.content[1].nome").value("Yoga"));
    }

    @Test
    @DisplayName("GET /api/corsi/{id}: dettaglio con l'istruttore")
    void dettaglioConIstruttore() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        yoga.setIstruttore(gino);
        corsi.save(yoga);
        ricarica();

        mockMvc.perform(get("/api/corsi/" + yoga.getId()).with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yoga.getId()))
                .andExpect(jsonPath("$.nome").value("Yoga"))
                .andExpect(jsonPath("$.livello").value("PRINCIPIANTE"))
                .andExpect(jsonPath("$.istruttore.id").value(gino.getId()))
                .andExpect(jsonPath("$.istruttore.cognome").value("Verdi"));
    }

    @Test
    @DisplayName("Un corso senza istruttore ha \"istruttore\": null")
    void corsoSenzaIstruttore() throws Exception {
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(get("/api/corsi/" + yoga.getId()).with(comeAdminApi()))
                .andExpect(jsonPath("$.istruttore").doesNotExist());   // il campo c'è ma vale null (nessun istruttore): doesNotExist() accetta anche il valore null
    }

    @Test
    @DisplayName("Un corso inesistente: 404 con il messaggio in JSON")
    void dettaglioInesistente() throws Exception {
        mockMvc.perform(get("/api/corsi/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Non trovato"))
                .andExpect(jsonPath("$.messaggio").value("Corso 999999 non trovato"));
    }

    @Test
    @DisplayName("Un id che non è un numero: 400, non un errore 500")
    void idNonNumerico() throws Exception {
        mockMvc.perform(get("/api/corsi/abc").with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messaggio").value(containsString("'id'")));
    }

    @Test
    @DisplayName("Senza token ogni endpoint risponde 401")
    void senzaToken() throws Exception {
        mockMvc.perform(get("/api/corsi")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/corsi").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/corsi/1")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------- CREAZIONE

    @Test
    @DisplayName("POST /api/corsi: 201 Created, intestazione Location e il corso nel corpo")
    void creare() throws Exception {
        String risposta = mockMvc.perform(conJson(post("/api/corsi"), corsoJson("Zumba", null)))
                .andExpect(status().isCreated())                                    // 201, non 200
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/corsi/\\d+")))
                .andExpect(jsonPath("$.nome").value("Zumba"))
                .andExpect(jsonPath("$.durataMinuti").value(55))
                .andExpect(jsonPath("$.livello").value("INTERMEDIO"))
                .andReturn().getResponse().getContentAsString();

        // Il corso è davvero nel database, con l'id indicato dalla risposta.
        Number id = JsonPath.read(risposta, "$.id");
        ricarica();
        assertThat(corsi.findById(id.longValue())).isPresent();
    }

    @Test
    @DisplayName("Corpo vuoto {}: 400 con un messaggio per ogni campo obbligatorio")
    void validazione() throws Exception {
        mockMvc.perform(conJson(post("/api/corsi"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Dati non validi"))
                .andExpect(jsonPath("$.campi.nome[0]").value("Il nome è obbligatorio"))
                .andExpect(jsonPath("$.campi.durataMinuti[0]").value("La durata è obbligatoria"))
                .andExpect(jsonPath("$.campi.livello[0]").value("Il livello è obbligatorio"));
    }

    @Test
    @DisplayName("Una durata 0 e un testo troppo lungo: i messaggi sui campi giusti")
    void validazioneRegole() throws Exception {
        String json = """
                {"nome":"Zumba","descrizione":"%s","durataMinuti":0,"livello":"AVANZATO"}
                """.formatted("x".repeat(300));

        mockMvc.perform(conJson(post("/api/corsi"), json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.durataMinuti[0]").value("La durata deve essere almeno 1 minuto"))
                .andExpect(jsonPath("$.campi.descrizione[0]").value("Massimo 255 caratteri"));
    }

    @Test
    @DisplayName("JSON malformato: 400 con un messaggio generico, senza dettagli tecnici")
    void jsonMalformato() throws Exception {
        mockMvc.perform(conJson(post("/api/corsi"), "{questo non è json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Richiesta non valida"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    @DisplayName("Un livello che non esiste: 400")
    void livelloInventato() throws Exception {
        mockMvc.perform(conJson(post("/api/corsi"),
                        "{\"nome\":\"Zumba\",\"durataMinuti\":55,\"livello\":\"ESPERTISSIMO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Richiesta non valida"));
    }

    @Test
    @DisplayName("Campi in più nel JSON (es. \"id\") sono ignorati: non si può forzare l'id")
    void campiInPiuIgnorati() throws Exception {
        String risposta = mockMvc.perform(conJson(post("/api/corsi"),
                        "{\"id\":999,\"nome\":\"Zumba\",\"durataMinuti\":55,\"livello\":\"AVANZATO\",\"campoInventato\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(risposta, "$.id");
        assertThat(id.longValue()).isNotEqualTo(999L);   // l'id lo assegna il database
    }

    // ------------------------------------------------------------- REGOLE SULL'ISTRUTTORE

    @Test
    @DisplayName("Si può creare un corso con un istruttore valido")
    void creareConIstruttore() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(conJson(post("/api/corsi"), corsoJson("Zumba", gino.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.istruttore.id").value(gino.getId()))
                .andExpect(jsonPath("$.istruttore.nome").value("Gino"));
    }

    @Test
    @DisplayName("Chi non ha il ruolo ISTRUTTORE non può essere istruttore: 400 sul campo istruttoreId")
    void istruttoreSenzaRuolo() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(conJson(post("/api/corsi"), corsoJson("Zumba", mario.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.istruttoreId[0]").value("L'utente scelto non ha il ruolo ISTRUTTORE"));
    }

    @Test
    @DisplayName("Un istruttore già iscritto al corso non può diventarne l'istruttore: 400")
    void istruttoreGiaIscritto() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        gino.setCorsi(new HashSet<>(Set.of(yoga)));
        utenti.save(gino);

        mockMvc.perform(conJson(put("/api/corsi/" + yoga.getId()), corsoJson("Yoga", gino.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.istruttoreId[0]").value(containsString("è iscritto a questo corso come allievo")));
    }

    // -------------------------------------------------------------------- MODIFICA

    @Test
    @DisplayName("PUT /api/corsi/{id}: modifica e restituisce il corso aggiornato")
    void modificare() throws Exception {
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(conJson(put("/api/corsi/" + yoga.getId()), corsoJson("Yoga Avanzato", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yoga.getId()))
                .andExpect(jsonPath("$.nome").value("Yoga Avanzato"))
                .andExpect(jsonPath("$.durataMinuti").value(55));

        ricarica();
        assertThat(corsi.findById(yoga.getId()).orElseThrow().getNome()).isEqualTo("Yoga Avanzato");
    }

    @Test
    @DisplayName("PUT sostituisce tutto: senza istruttoreId l'istruttore viene tolto")
    void putSostituisceTutto() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        Corso yoga = creaCorso("Yoga");
        yoga.setIstruttore(gino);
        corsi.save(yoga);
        ricarica();

        mockMvc.perform(conJson(put("/api/corsi/" + yoga.getId()), corsoJson("Yoga", null)))
                .andExpect(status().isOk());

        ricarica();
        assertThat(corsi.findWithIstruttoreById(yoga.getId()).orElseThrow().getIstruttore()).isNull();
    }

    @Test
    @DisplayName("PUT su un corso inesistente: 404")
    void modificareInesistente() throws Exception {
        mockMvc.perform(conJson(put("/api/corsi/999999"), corsoJson("Yoga", null)))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- ELIMINAZIONE

    @Test
    @DisplayName("DELETE /api/corsi/{id}: 204 No Content e il corso non c'è più")
    void eliminare() throws Exception {
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(delete("/api/corsi/" + yoga.getId()).with(comeAdminApi()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));            // 204 = nessun corpo

        mockMvc.perform(get("/api/corsi/" + yoga.getId()).with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE di un corso con iscritti: 409 Conflict con il motivo")
    void eliminareConIscritti() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        mario.setCorsi(new HashSet<>(Set.of(yoga)));
        utenti.save(mario);

        mockMvc.perform(delete("/api/corsi/" + yoga.getId()).with(comeAdminApi()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("Operazione non consentita"))
                .andExpect(jsonPath("$.messaggio").value(containsString("1 utenti iscritti")));
    }

    @Test
    @DisplayName("DELETE di un corso inesistente: 404")
    void eliminareInesistente() throws Exception {
        mockMvc.perform(delete("/api/corsi/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }
}
