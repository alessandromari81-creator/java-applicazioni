/*
 * FILE: RicercaSqlTest.java
 * A COSA SERVE: tiene allineati il CODICE della ricerca e gli INDICI del database. Gli indici di ricerca sugli utenti (migration V10)
 * sono costruiti su upper(nome), upper(cognome) e upper(email): il database li usa solo se la query contiene ESATTAMENTE quelle espressioni.
 * Se un giorno la ricerca cambiasse forma (es. lower al posto di upper) gli indici smetterebbero di servire senza nessun errore visibile:
 * la ricerca resterebbe corretta ma tornerebbe lenta. Questo test lo fa fallire subito.
 * (H2 non ha indici trigram: qui si controlla il testo SQL generato, non i tempi; i tempi si misurano su PostgreSQL.)
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RicercaSqlTest extends BaseApiTest {

    // upper(alias.colonna) like upper(?)  per ciascuna delle tre colonne
    private static final Pattern NOME = Pattern.compile("upper\\(\\w+\\.nome\\) like upper\\(\\?\\)");
    private static final Pattern COGNOME = Pattern.compile("upper\\(\\w+\\.cognome\\) like upper\\(\\?\\)");
    private static final Pattern EMAIL = Pattern.compile("upper\\(\\w+\\.email\\) like upper\\(\\?\\)");

    @BeforeEach
    void azzera() {
        RegistratoreSql.svuota();
    }

    private void assertUsaUpperSuTutteLeColonne(List<String> istruzioni) {
        assertThat(istruzioni).as("istruzioni SQL con LIKE registrate").isNotEmpty();
        String unite = String.join("\n", istruzioni);
        assertThat(NOME.matcher(unite).find()).as("upper(nome) like upper(?)").isTrue();
        assertThat(COGNOME.matcher(unite).find()).as("upper(cognome) like upper(?)").isTrue();
        assertThat(EMAIL.matcher(unite).find()).as("upper(email) like upper(?)").isTrue();
        assertThat(unite).as("niente lower(...)").doesNotContainIgnoringCase("lower(");
    }

    @Test
    @DisplayName("La ricerca degli utenti confronta upper(nome), upper(cognome) e upper(email): la forma su cui sono costruiti gli indici")
    void ricercaUtentiUsaUpper() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(get("/api/utenti").param("q", "ross").with(comeAdminApi())).andExpect(status().isOk());

        assertUsaUpperSuTutteLeColonne(RegistratoreSql.istruzioniConLike());
    }

    @Test
    @DisplayName("Anche la ricerca negli abbonamenti (per iscritto) usa upper() sulle colonne dell'utente")
    void ricercaAbbonamentiUsaUpper() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, LocalDate.now());

        mockMvc.perform(get("/api/abbonamenti").param("q", "ross").with(comeAdminApi())).andExpect(status().isOk());

        assertUsaUpperSuTutteLeColonne(RegistratoreSql.istruzioniConLike());
    }

    @Test
    @DisplayName("La migration V10 crea gli indici proprio su upper(nome), upper(cognome), upper(email)")
    void laMigrationCorrispondeAllaQuery() throws IOException {
        String migration = new String(new ClassPathResource("db/migration/V10__indici_ricerca_con_upper.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("USING gin (upper(nome) gin_trgm_ops)")
                .contains("USING gin (upper(cognome) gin_trgm_ops)")
                .contains("USING gin (upper(email) gin_trgm_ops)");
    }
}
