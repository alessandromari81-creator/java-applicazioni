/*
 * FILE: ApiAbbonamentiTest.java
 * A COSA SERVE: verifica la API REST degli abbonamenti: lista generale, storico di un utente, "primo giorno libero",
 * creazione (con data di fine e prezzo calcolati), le regole di business (solo ISCRITTO, niente passato, niente
 * sovrapposizioni, si eliminano solo i non ancora iniziati) e i codici di risposta (201, 400, 404, 409).
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO. Le date sono RELATIVE a oggi.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Abbonamento;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiAbbonamentiTest extends BaseApiTest {

    private final LocalDate oggi = LocalDate.now();

    private Utente creaIscritto(String nome, String cognome, String email) {
        return creaUtente(nome, cognome, email, ruolo("ISCRITTO"));
    }

    // JSON per creare un abbonamento: tipo e data di inizio (aaaa-mm-gg).
    private String abbonamentoJson(String tipo, LocalDate inizio) {
        return "{\"tipo\":\"%s\",\"dataInizio\":\"%s\"}".formatted(tipo, inizio);
    }

    private String urlUtente(Utente u) {
        return "/api/utenti/" + u.getId() + "/abbonamenti";
    }

    // ------------------------------------------------------------------------ LETTURA

    @Test
    @DisplayName("GET /api/abbonamenti: tutti gli abbonamenti, con l'iscritto e lo stato")
    void elenco() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(get("/api/abbonamenti").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].utente.cognome").value("Rossi"))
                .andExpect(jsonPath("$.content[0].tipo").value("MENSILE"))
                .andExpect(jsonPath("$.content[0].prezzo").value(40.0))
                .andExpect(jsonPath("$.content[0].stato").value("ATTIVO"));
    }

    @Test
    @DisplayName("GET /api/abbonamenti/{id}: dettaglio, con date in formato aaaa-mm-gg")
    void dettaglio() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento a = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(get("/api/abbonamenti/" + a.getId()).with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInizio").value(oggi.toString()))                      // data ISO, non numeri
                .andExpect(jsonPath("$.dataFine").value(TipoAbbonamento.MENSILE.calcolaFine(oggi).toString()));
    }

    @Test
    @DisplayName("Un abbonamento inesistente: 404")
    void inesistente() throws Exception {
        mockMvc.perform(get("/api/abbonamenti/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messaggio").value("Abbonamento 999999 non trovato"));
    }

    @Test
    @DisplayName("GET /api/utenti/{id}/abbonamenti: solo lo storico di quell'utente, dal più recente")
    void storicoDiUnUtente() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusDays(90));
        creaAbbonamento(mario, TipoAbbonamento.ANNUALE, oggi);
        creaAbbonamento(laura, TipoAbbonamento.TRIMESTRALE, oggi);

        mockMvc.perform(get(urlUtente(mario)).with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))                         // solo i due di Mario
                .andExpect(jsonPath("$.content[0].tipo").value("ANNUALE"))           // il più recente per primo
                .andExpect(jsonPath("$.content[1].tipo").value("MENSILE"))
                .andExpect(jsonPath("$.content[0].utente.id").value(mario.getId()));
    }

    @Test
    @DisplayName("Lo storico di un utente inesistente è 404; quello di un utente senza abbonamenti è una lista vuota")
    void storicoVuotoOInesistente() throws Exception {
        Utente laura = creaIscritto("Laura", "Bianchi", "laura@example.com");

        mockMvc.perform(get(urlUtente(laura)).with(comeAdminApi()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/utenti/999999/abbonamenti").with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET .../primo-giorno-libero: oggi se non ha abbonamenti, altrimenti il giorno dopo la scadenza")
    void primoGiornoLibero() throws Exception {
        Utente senza = creaIscritto("Laura", "Bianchi", "laura@example.com");
        Utente con = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(con, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(get(urlUtente(senza) + "/primo-giorno-libero").with(comeAdminApi()))
                .andExpect(jsonPath("$.data").value(oggi.toString()));
        mockMvc.perform(get(urlUtente(con) + "/primo-giorno-libero").with(comeAdminApi()))
                .andExpect(jsonPath("$.data").value(attuale.getDataFine().plusDays(1).toString()));
    }

    @Test
    @DisplayName("Senza token: 401")
    void senzaToken() throws Exception {
        mockMvc.perform(get("/api/abbonamenti")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------- CREAZIONE

    @Test
    @DisplayName("POST: 201, Location, e fine / prezzo / stato calcolati dal server")
    void creare() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("TRIMESTRALE", oggi)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/abbonamenti/\\d+")))
                .andExpect(jsonPath("$.tipo").value("TRIMESTRALE"))
                .andExpect(jsonPath("$.dataFine").value(TipoAbbonamento.TRIMESTRALE.calcolaFine(oggi).toString()))
                .andExpect(jsonPath("$.prezzo").value(105.0))
                .andExpect(jsonPath("$.stato").value("ATTIVO"))
                .andExpect(jsonPath("$.utente.nome").value("Mario"));

        ricarica();
        assertThat(abbonamenti.findByUtenteIdOrderByDataInizioDesc(mario.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Un abbonamento che parte nel futuro ha stato FUTURO")
    void creareFuturo() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("MENSILE", oggi.plusDays(10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stato").value("FUTURO"));
    }

    @Test
    @DisplayName("Dati mancanti: 400 con un messaggio per campo")
    void campiMancanti() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(conJson(post(urlUtente(mario)), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.tipo[0]").value("Il tipo è obbligatorio"))
                .andExpect(jsonPath("$.campi.dataInizio[0]").value("La data di inizio è obbligatoria"));
    }

    @Test
    @DisplayName("Un tipo inesistente o una data scritta male: 400 con messaggio generico")
    void valoriNonValidi() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(conJson(post(urlUtente(mario)), "{\"tipo\":\"BIMESTRALE\",\"dataInizio\":\"" + oggi + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Richiesta non valida"));
        mockMvc.perform(conJson(post(urlUtente(mario)), "{\"tipo\":\"MENSILE\",\"dataInizio\":\"09/11/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messaggio").value(containsString("aaaa-mm-gg")));   // il formato atteso
    }

    // ------------------------------------------------------------- LE REGOLE DI BUSINESS

    @Test
    @DisplayName("Regola: solo gli ISCRITTI possono avere un abbonamento (400 sul campo utente)")
    void soloGliIscritti() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));

        mockMvc.perform(conJson(post(urlUtente(gino)), abbonamentoJson("MENSILE", oggi)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.utente[0]").value(containsString("Solo gli utenti con ruolo ISCRITTO")));
    }

    @Test
    @DisplayName("Un utente inesistente: 404")
    void utenteInesistente() throws Exception {
        mockMvc.perform(conJson(post("/api/utenti/999999/abbonamenti"), abbonamentoJson("MENSILE", oggi)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Regola: la data di inizio non può essere nel passato (400 sul campo dataInizio)")
    void nonNelPassato() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");

        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("MENSILE", oggi.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.dataInizio[0]").value(containsString("non può essere nel passato")));
    }

    @Test
    @DisplayName("Regola: niente sovrapposizioni, e il messaggio indica il primo giorno libero")
    void nonSiSovrappone() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("MENSILE", oggi.plusDays(5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.dataInizio[0]").value(containsString("Si sovrappone")))
                .andExpect(jsonPath("$.campi.dataInizio[0]").value(containsString("primo giorno libero")));

        ricarica();
        assertThat(abbonamenti.count()).isEqualTo(1);       // c'è ancora solo l'esistente
        assertThat(attuale.getId()).isNotNull();
    }

    @Test
    @DisplayName("Il rinnovo il giorno dopo la scadenza è ammesso; partire nel giorno di scadenza no")
    void rinnovoAlConfine() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attuale = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);

        // La fine è INCLUSA: partire nello stesso giorno di scadenza è una sovrapposizione (di un giorno)...
        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("MENSILE", attuale.getDataFine())))
                .andExpect(status().isBadRequest());
        // ...il giorno dopo invece va bene.
        mockMvc.perform(conJson(post(urlUtente(mario)), abbonamentoJson("MENSILE", attuale.getDataFine().plusDays(1))))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------------------------- ELIMINAZIONE

    @Test
    @DisplayName("DELETE di un abbonamento non ancora iniziato: 204")
    void eliminareUnoFuturo() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusDays(10));

        mockMvc.perform(delete("/api/abbonamenti/" + futuro.getId()).with(comeAdminApi()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/abbonamenti/" + futuro.getId()).with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE di un abbonamento attivo o scaduto: 409 (restano nello storico)")
    void nonSiEliminanoGliAltri() throws Exception {
        Utente mario = creaIscritto("Mario", "Rossi", "mario@example.com");
        Abbonamento attivo = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi);
        Abbonamento scaduto = creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.minusDays(90));

        for (Abbonamento a : new Abbonamento[] {attivo, scaduto}) {
            mockMvc.perform(delete("/api/abbonamenti/" + a.getId()).with(comeAdminApi()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.messaggio").value(containsString("solo gli abbonamenti non ancora iniziati")));
        }
    }

    @Test
    @DisplayName("DELETE di un abbonamento inesistente: 404")
    void eliminareInesistente() throws Exception {
        mockMvc.perform(delete("/api/abbonamenti/999999").with(comeAdminApi()))
                .andExpect(status().isNotFound());
    }
}
