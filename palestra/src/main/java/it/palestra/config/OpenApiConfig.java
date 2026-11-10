/*
 * FILE: OpenApiConfig.java
 * A COSA SERVE: descrive la API a Swagger/OpenAPI (springdoc). springdoc legge già da solo i controller di /api (percorsi, parametri,
 * DTO di richiesta e risposta); qui si aggiunge quello che da solo non può sapere:
 *   1) titolo/descrizione della documentazione;
 *   2) lo "schema di sicurezza": la API vuole l'intestazione  Authorization: Bearer <access_token>  (nella pagina Swagger compare il
 *      pulsante "Authorize" dove incollare il token preso da POST /api/auth/login);
 *   3) le risposte di errore comuni (400, 401, 403, 404, 409), tutte nella forma ErroreApi, aggiunte a ogni operazione.
 * COME SI VEDE: dopo aver fatto login nel pannello (la documentazione è riservata agli ADMIN, vedi SecurityConfig):
 *   http://localhost:8080/swagger-ui.html   (pagina interattiva)   e   http://localhost:8080/v3/api-docs   (JSON OpenAPI, importabile in Postman).
 */
package it.palestra.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import it.palestra.api.dto.ErroreApi;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Nome interno dello schema di sicurezza (si richiama nelle operazioni protette).
    private static final String SCHEMA_BEARER = "bearerAuth";

    // L'intestazione generale del documento OpenAPI + lo schema di sicurezza + l'obbligo di token su TUTTE le operazioni
    // (le tre di /api/auth/* lo tolgono, vedi il customizer qui sotto). "bearerFormat JWT" è solo un'etichetta per chi legge.
    @Bean
    OpenAPI descrizioneApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API REST della Palestra")
                        .version("1.0")
                        .description("Corsi, utenti (iscritti/istruttori/admin) e abbonamenti. Tutto richiede il ruolo ADMIN: fai "
                                + "POST /api/auth/login, copia l'access_token e premi «Authorize». Le liste sono paginate "
                                + "(page, size, sort, q): risposta {content, number, size, totalElements, totalPages, first, last, sort, q}."))
                .components(new Components().addSecuritySchemes(SCHEMA_BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_BEARER));
    }

    // Per ogni operazione aggiunge le risposte di errore che la API può dare, con la forma ErroreApi (status, errore, messaggio, campi).
    // Le regole sono quelle di GestoreErroriApi e SecurityConfig:
    //   400 dati/parametri non validi (c'è un corpo da validare, oppure una lista con page/size/sort/q);
    //   401 token assente o scaduto, 403 ruolo insufficiente (non per /api/auth/*, che è pubblico);
    //   404 risorsa inesistente (percorso con {id});  409 regola di business violata (ogni scrittura che non sia /api/auth/*).
    @Bean
    OpenApiCustomizer risposteDiErrore() {
        return openApi -> {
            // Lo schema ErroreApi va registrato nei "components" per poterlo richiamare da ogni risposta.
            var schemi = ModelConverters.getInstance()
                    .readAll(new AnnotatedType(ErroreApi.class));
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            schemi.forEach((nome, schema) -> openApi.getComponents().addSchemas(nome, schema));
            Schema<?> riferimento = new Schema<>().$ref("#/components/schemas/ErroreApi");

            openApi.getPaths().forEach((percorso, voce) -> voce.readOperationsMap().forEach((metodo, operazione) -> {
                boolean pubblica = percorso.startsWith("/api/auth/");
                boolean scrittura = !"GET".equals(metodo.name());
                if (pubblica) operazione.setSecurity(java.util.List.of());      // niente lucchetto sulle operazioni pubbliche
                boolean haCorpo = operazione.getRequestBody() != null;
                boolean listaConParametri = "GET".equals(metodo.name()) && operazione.getParameters() != null
                        && operazione.getParameters().stream().anyMatch(p -> "page".equals(p.getName()));

                if (haCorpo || listaConParametri) aggiungi(operazione, "400", "Dati o parametri non validi (il dettaglio è in «campi»)", riferimento);
                if (!pubblica) {
                    aggiungi(operazione, "401", "Token assente, scaduto o non valido", riferimento);
                    aggiungi(operazione, "403", "Il token non ha il ruolo ADMIN", riferimento);
                }
                if ("/api/auth/login".equals(percorso)) aggiungi(operazione, "429", "Troppi tentativi di login falliti: riprova dopo i secondi indicati in Retry-After", riferimento);
                if (percorso.contains("{")) aggiungi(operazione, "404", "Risorsa inesistente", riferimento);
                if (scrittura && !pubblica) aggiungi(operazione, "409", "Regola di business violata", riferimento);
            }));
        };
    }

    // Aggiunge una risposta di errore all'operazione, salvo che il controller ne abbia già dichiarata una con lo stesso codice.
    private void aggiungi(Operation operazione, String codice, String descrizione, Schema<?> schema) {
        if (operazione.getResponses().containsKey(codice)) return;
        operazione.getResponses().addApiResponse(codice, new ApiResponse().description(descrizione)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
