/*
 * FILE: IndirizzoUtenteTest.java
 * A COSA SERVE: verifica l'indirizzo di casa degli utenti (indirizzo, CAP, città) nel pannello di amministrazione e nella API: salvataggio, rilettura,
 * testo vuoto = nessun valore, limiti di lunghezza. I campi sono facoltativi.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.Utente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndirizzoUtenteTest extends BaseApiTest {

    @Test
    @DisplayName("Dal pannello: l'indirizzo si salva (con gli spazi tolti), si rilegge nel form di modifica e un testo vuoto diventa nessun valore")
    void pannello() throws Exception {
        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "  Via Roma 1 ").param("cap", "00100").param("citta", "Roma"))
                .andExpect(status().is3xxRedirection());
        ricarica();
        Utente mario = utenti.findByEmailIgnoreCase("mario@example.com").orElseThrow();
        assertThat(mario.getIndirizzo()).isEqualTo("Via Roma 1");
        assertThat(mario.getCap()).isEqualTo("00100");
        assertThat(mario.getCitta()).isEqualTo("Roma");

        mockMvc.perform(get("/utenti/" + mario.getId() + "/modifica").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Via Roma 1")));

        mockMvc.perform(post("/utenti/" + mario.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "   ").param("cap", "").param("citta", ""))
                .andExpect(status().is3xxRedirection());
        ricarica();
        Utente dopo = utenti.findById(mario.getId()).orElseThrow();
        assertThat(dopo.getIndirizzo()).isNull();
        assertThat(dopo.getCap()).isNull();
        assertThat(dopo.getCitta()).isNull();
    }

    @Test
    @DisplayName("Dal pannello: indirizzo troppo lungo = errore sul campo, niente 500")
    void pannelloLimiti() throws Exception {
        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "x".repeat(256)).param("cap", "123456789012").param("citta", "y".repeat(101)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("utenteForm", "indirizzo", "cap", "citta"));
    }

    @Test
    @DisplayName("Dalla API: l'indirizzo è nella risposta; PUT sostituisce tutti i campi, quindi un PUT senza indirizzo lo svuota")
    void api() throws Exception {
        String creato = mockMvc.perform(conJson(post("/api/utenti"),
                        "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"mario@example.com\",\"ruoli\":[\"ADMIN\"],"
                                + "\"password\":\"password-lunga-1\",\"indirizzo\":\"Via Roma 1\",\"cap\":\"00100\",\"citta\":\"Roma\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.indirizzo").value("Via Roma 1"))
                .andExpect(jsonPath("$.cap").value("00100"))
                .andExpect(jsonPath("$.citta").value("Roma"))
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(creato, "$.id");

        mockMvc.perform(conJson(put("/api/utenti/" + id),
                        "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"mario@example.com\",\"ruoli\":[\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo").doesNotExist());
    }

    @Test
    @DisplayName("Dalla API: un indirizzo troppo lungo è un 400 con il campo indicato")
    void apiLimiti() throws Exception {
        mockMvc.perform(conJson(post("/api/utenti"),
                        "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"mario@example.com\",\"ruoli\":[\"ISCRITTO\"],"
                                + "\"indirizzo\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.indirizzo").exists());
    }
}
