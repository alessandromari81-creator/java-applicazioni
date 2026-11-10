/*
 * FILE: OpenApiTest.java
 * A COSA SERVE: verifica la documentazione Swagger/OpenAPI: il JSON /v3/api-docs descrive tutti gli endpoint della API (e SOLO quelli),
 * con lo schema Bearer, i parametri di paginazione, le risposte di errore; e che la documentazione sia riservata agli ADMIN
 * (non pubblica) come il resto del pannello.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiTest extends BaseWebTest {

    private String documento() throws Exception {
        return mockMvc.perform(get("/v3/api-docs").with(comeAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Il documento OpenAPI elenca gli endpoint della API e solo quelli (non le pagine HTML del pannello)")
    void descriveTuttiGliEndpoint() throws Exception {
        String json = documento();
        Map<String, Object> percorsi = JsonPath.read(json, "$.paths");

        assertThat(percorsi.keySet()).contains(
                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                "/api/corsi", "/api/corsi/{id}",
                "/api/utenti", "/api/utenti/{id}",
                "/api/abbonamenti", "/api/abbonamenti/{id}",
                "/api/utenti/{utenteId}/abbonamenti", "/api/utenti/{utenteId}/abbonamenti/primo-giorno-libero");
        assertThat(percorsi.keySet()).allMatch(p -> p.startsWith("/api/"));      // niente /utenti, /corsi, /login ...
        assertThat(JsonPath.<String>read(json, "$.info.title")).isEqualTo("API REST della Palestra");
    }

    @Test
    @DisplayName("Schema di sicurezza Bearer; login/refresh/logout senza lucchetto, tutto il resto con token")
    void sicurezzaBearer() throws Exception {
        String json = documento();

        assertThat(JsonPath.<String>read(json, "$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
        assertThat(JsonPath.<List<Object>>read(json, "$.paths['/api/auth/login'].post.security")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(json, "$.paths['/api/auth/refresh'].post.security")).isEmpty();
        // Le operazioni protette ereditano la richiesta globale: il documento la dichiara e non è svuotata nell'operazione.
        assertThat(JsonPath.<List<Object>>read(json, "$.security[*].bearerAuth")).isNotEmpty();
        assertThat(documentoHa(json, "$.paths['/api/corsi'].get.security")).isFalse();
    }

    private boolean documentoHa(String json, String percorso) {
        try {
            JsonPath.read(json, percorso);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Le liste documentano page, size, sort, q con i campi ammessi")
    void parametriDiPaginazione() throws Exception {
        String json = documento();

        List<String> nomi = JsonPath.read(json, "$.paths['/api/utenti'].get.parameters[*].name");
        assertThat(nomi).contains("page", "size", "sort", "q");
        List<String> descrizioni = JsonPath.read(json, "$.paths['/api/utenti'].get.parameters[?(@.name=='sort')].description");
        assertThat(descrizioni.get(0)).contains("cognome").contains("email");
        // Lo storico di un utente non ha la ricerca.
        List<String> storico = JsonPath.read(json, "$.paths['/api/utenti/{utenteId}/abbonamenti'].get.parameters[*].name");
        assertThat(storico).contains("page", "size", "sort").doesNotContain("q");
    }

    @Test
    @DisplayName("Le risposte di errore sono documentate con la forma ErroreApi")
    void risposteDiErrore() throws Exception {
        String json = documento();

        assertThat(JsonPath.<Map<String, Object>>read(json, "$.paths['/api/corsi/{id}'].delete.responses").keySet())
                .contains("401", "403", "404", "409");
        assertThat(JsonPath.<Map<String, Object>>read(json, "$.paths['/api/corsi'].get.responses").keySet())
                .contains("400", "401", "403").doesNotContain("404", "409");
        assertThat(JsonPath.<Map<String, Object>>read(json, "$.paths['/api/auth/login'].post.responses").keySet())
                .contains("400").doesNotContain("401", "403");
        assertThat(JsonPath.<String>read(json, "$.paths['/api/corsi'].get.responses['401'].content['application/json'].schema['$ref']"))
                .isEqualTo("#/components/schemas/ErroreApi");
        assertThat(JsonPath.<Map<String, Object>>read(json, "$.components.schemas.ErroreApi.properties").keySet())
                .contains("status", "errore", "messaggio", "campi");
    }

    @Test
    @DisplayName("Le creazioni (POST) sono documentate con 201 Created e non con 200")
    void creazioniDocumentateConCodice201() throws Exception {
        String json = documento();

        for (String percorso : List.of("/api/corsi", "/api/utenti", "/api/utenti/{utenteId}/abbonamenti")) {
            Map<String, Object> risposte = JsonPath.read(json, "$.paths['" + percorso + "'].post.responses");
            assertThat(risposte.keySet()).as(percorso).contains("201").doesNotContain("200");
        }
    }

    @Test
    @DisplayName("La documentazione NON è pubblica: senza login si va alla pagina di login, senza ruolo ADMIN è 403")
    void documentazioneRiservataAdmin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        mockMvc.perform(get("/v3/api-docs").with(user("iscritto@x.it").roles("ISCRITTO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un admin loggato apre la pagina interattiva Swagger")
    void paginaSwaggerPerAdmin() throws Exception {
        // /swagger-ui.html rimanda alla pagina vera (/swagger-ui/index.html), che deve essere raggiungibile dall'admin.
        mockMvc.perform(get("/swagger-ui.html").with(comeAdmin()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html").with(comeAdmin()))
                .andExpect(status().isOk());
    }
}
