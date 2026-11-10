/*
 * FILE: ApiPaginazioneTest.java
 * A COSA SERVE: verifica la paginazione delle liste della API (utenti, corsi, abbonamenti e storico di un utente):
 *   - la risposta contiene gli elementi e i dati per navigare (numero pagina, dimensione, totali, prima/ultima);
 *   - l'ordine è STABILE: passando da una pagina all'altra nessuna riga compare due volte o sparisce, anche con valori uguali;
 *   - ordinamento solo sui campi ammessi, parametri non validi = 400;
 *   - ricerca di testo (parziale, senza maiuscole, con % e _ trattati come caratteri normali);
 *   - le relazioni arrivano complete e le query sono POCHE e non crescono con il numero di righe ("query in due passi").
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import it.palestra.model.Corso;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ApiPaginazioneTest extends BaseApiTest {

    @Autowired EntityManagerFactory emf;

    private final LocalDate oggi = LocalDate.now();

    // ----------------------------------------------------------------- METODI DI AIUTO

    // Crea "quanti" iscritti: nomi Nome00.., cognomi con il prefisso dato + numero (es. Cognome00, Cognome01...).
    private List<Utente> creaIscritti(int quanti, String prefissoCognome) {
        List<Utente> creati = new ArrayList<>();
        for (int i = 0; i < quanti; i++) {
            String nn = String.format("%02d", i);
            creati.add(creaUtente("Nome" + nn, prefissoCognome + nn, "u" + prefissoCognome.toLowerCase() + nn + "@example.com", ruolo("ISCRITTO")));
        }
        return creati;
    }

    // Legge dal JSON della risposta un campo di tutti gli elementi di "content" (es. tutti gli id).
    private <T> List<T> campi(String json, String campo) {
        return JsonPath.read(json, "$.content[*]." + campo);
    }

    private String json(String url, String... paramNomeValore) throws Exception {
        var richiesta = get(url).with(comeAdminApi());
        for (int i = 0; i < paramNomeValore.length; i += 2) richiesta.param(paramNomeValore[i], paramNomeValore[i + 1]);
        return mockMvc.perform(richiesta).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Statistics statistiche() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    // ------------------------------------------------------------ METADATI DELLA PAGINA

    @Test
    @DisplayName("La risposta contiene gli elementi e i dati per navigare: numero, dimensione, totali, prima/ultima")
    void metadatiDellaPagina() throws Exception {
        creaIscritti(45, "Cognome");                      // + l'admin iniziale = 46 utenti in tutto

        mockMvc.perform(get("/api/utenti").param("size", "10").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(46))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.sort").value("cognome,asc"))        // l'ordinamento applicato (quello di partenza)
                .andExpect(jsonPath("$.q").doesNotExist());                // nessuna ricerca: il campo non compare

        // L'ultima pagina ha solo gli elementi che restano (46 = 4 pagine da 10 + 6) ed è "last".
        mockMvc.perform(get("/api/utenti").param("size", "10").param("page", "4").with(comeAdminApi()))
                .andExpect(jsonPath("$.content", hasSize(6)))
                .andExpect(jsonPath("$.number").value(4))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("Senza parametri: pagina 0 da 20 elementi")
    void valoriPredefiniti() throws Exception {
        creaIscritti(30, "Cognome");

        mockMvc.perform(get("/api/utenti").with(comeAdminApi()))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(2));            // 31 utenti / 20
    }

    @Test
    @DisplayName("Una pagina oltre l'ultima: 200 con elenco vuoto e i totali corretti (non un errore)")
    void paginaOltreLUltima() throws Exception {
        creaIscritti(5, "Cognome");

        mockMvc.perform(get("/api/utenti").param("page", "99").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.number").value(99))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    @DisplayName("size=100 è il massimo ammesso")
    void sizeMassima() throws Exception {
        mockMvc.perform(get("/api/utenti").param("size", "100").with(comeAdminApi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ----------------------------------------------------------------- ORDINE STABILE

    @Test
    @DisplayName("Ordine STABILE: con valori tutti uguali ogni riga compare una e una sola volta scorrendo le pagine")
    void ordineStabileConValoriUguali() throws Exception {
        // PREPARO: 25 utenti con lo STESSO cognome e lo STESSO nome: l'unico modo per distinguerli nell'ordinamento è l'id.
        List<Utente> uguali = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            uguali.add(creaUtente("Stesso", "Uguale", "uguale" + i + "@example.com", ruolo("ISCRITTO")));
        }
        Set<Long> attesi = new HashSet<>();
        uguali.forEach(u -> attesi.add(u.getId()));

        // ESEGUO: scorro tutte le pagine da 10 con la ricerca "Uguale" (3 pagine: 10 + 10 + 5).
        List<Long> trovati = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            String risposta = json("/api/utenti", "q", "Uguale", "size", "10", "page", String.valueOf(pagina));
            List<Number> ids = campi(risposta, "id");
            ids.forEach(id -> trovati.add(id.longValue()));
        }

        // VERIFICO: 25 elementi, tutti diversi, e sono esattamente quelli creati (nessuno doppio, nessuno mancante).
        assertThat(trovati).hasSize(25).doesNotHaveDuplicates();
        assertThat(new HashSet<>(trovati)).isEqualTo(attesi);
        assertThat(trovati).isSorted();                                // spareggio sull'id crescente
    }

    // -------------------------------------------------------------------- ORDINAMENTO

    @Test
    @DisplayName("Ordinamento crescente e decrescente su un campo ammesso, e la risposta lo riporta")
    void ordinamento() throws Exception {
        creaIscritti(12, "Cognome");

        String asc = json("/api/utenti", "sort", "email,asc");
        String desc = json("/api/utenti", "sort", "email,desc");

        List<String> emailAsc = campi(asc, "email");
        List<String> emailDesc = campi(desc, "email");
        assertThat(emailAsc).isSortedAccordingTo((a, b) -> a.compareTo(b));
        assertThat(emailDesc).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(JsonPath.<String>read(desc, "$.sort")).isEqualTo("email,desc");
    }

    @Test
    @DisplayName("Un campo non ammesso (es. passwordHash) o una direzione sbagliata: 400 con l'elenco dei campi ammessi")
    void ordinamentoNonAmmesso() throws Exception {
        mockMvc.perform(get("/api/utenti").param("sort", "passwordHash").with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Parametri non validi"))
                .andExpect(jsonPath("$.campi.sort[0]").value(containsString("Campi ammessi")))
                .andExpect(jsonPath("$.campi.sort[0]").value(containsString("cognome")));

        mockMvc.perform(get("/api/utenti").param("sort", "cognome,su").with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.sort").exists());
        mockMvc.perform(get("/api/corsi").param("sort", "istruttore").with(comeAdminApi()))      // non ammesso nei corsi
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------- PARAMETRI NON VALIDI

    @Test
    @DisplayName("page negativa, size 0 o oltre 100, valori non numerici: 400 che dice quali parametri sono sbagliati")
    void parametriNonValidi() throws Exception {
        mockMvc.perform(get("/api/utenti").param("page", "-1").param("size", "0").with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.page").exists())               // entrambi segnalati insieme
                .andExpect(jsonPath("$.campi.size[0]").value(containsString("1 e 100")));

        mockMvc.perform(get("/api/utenti").param("size", "101").with(comeAdminApi())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/corsi").param("size", "abc").with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messaggio").value(containsString("'size'")));
        mockMvc.perform(get("/api/abbonamenti").param("page", "x").with(comeAdminApi())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------ RICERCA

    @Test
    @DisplayName("Ricerca: testo parziale, senza distinguere maiuscole, in nome, cognome o email; i totali riguardano il filtro")
    void ricerca() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaUtente("Maria", "Rossini", "maria@example.com", ruolo("ISCRITTO"));
        creaUtente("Luca", "Verdi", "luca.verdi@esempio.it", ruolo("ISCRITTO"));

        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "ross"), "$.totalElements")).isEqualTo(2);   // cognome, parziale
        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "ROSS"), "$.totalElements")).isEqualTo(2);   // maiuscole
        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "mari"), "$.totalElements")).isEqualTo(2);   // nome: Mario, Maria
        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "esempio.it"), "$.totalElements")).isEqualTo(1);   // email

        mockMvc.perform(get("/api/utenti").param("q", "zzzzz").with(comeAdminApi()))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("La ricerca si combina con la paginazione e viene riportata nella risposta")
    void ricercaEPaginazione() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaUtente("Maria", "Rossini", "maria@example.com", ruolo("ISCRITTO"));
        creaUtente("Luca", "Verdi", "luca@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(get("/api/utenti").param("q", "  ross ").param("size", "1").with(comeAdminApi()))   // spazi ignorati
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.q").value("ross"));
    }

    @Test
    @DisplayName("I caratteri speciali % e _ sono cercati come caratteri normali (non come jolly)")
    void caratteriJolly() throws Exception {
        creaUtente("A", "Uno", "a_b@example.com", ruolo("ISCRITTO"));
        creaUtente("B", "Due", "axb@example.com", ruolo("ISCRITTO"));

        // Se "_" fosse un jolly, "a_b" troverebbe anche "axb@...": invece trova solo l'email con il vero trattino basso.
        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "a_b"), "$.totalElements")).isEqualTo(1);
        // "%" cercato da solo non deve corrispondere a tutto.
        assertThat(JsonPath.<Integer>read(json("/api/utenti", "q", "%"), "$.totalElements")).isZero();
    }

    @Test
    @DisplayName("Una ricerca più lunga di 100 caratteri: 400")
    void ricercaTroppoLunga() throws Exception {
        mockMvc.perform(get("/api/utenti").param("q", "x".repeat(101)).with(comeAdminApi()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi.q").exists());
    }

    // ------------------------------------------------- RELAZIONI COMPLETE E POCHE QUERY

    @Test
    @DisplayName("Gli utenti di una pagina arrivano con ruoli, corsi e corsi tenuti COMPLETI, nell'ordine giusto")
    void relazioniComplete() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        Utente gino = creaUtente("Gino", "Aaa", "gino@example.com", ruolo("ISTRUTTORE"), ruolo("ISCRITTO"));
        gino.setCorsi(new HashSet<>(Set.of(pilates)));                      // frequenta Pilates...
        utenti.save(gino);
        yoga.setIstruttore(gino);                                           // ...e tiene Yoga
        corsi.save(yoga);
        creaUtente("Mario", "Bbb", "mario@example.com", ruolo("ISCRITTO"));
        ricarica();

        String risposta = json("/api/utenti", "size", "2");                  // Aaa e Bbb
        assertThat(JsonPath.<List<String>>read(risposta, "$.content[*].cognome")).containsExactly("Aaa", "Bbb");   // ordine della pagina
        assertThat(JsonPath.<List<String>>read(risposta, "$.content[0].ruoli")).containsExactly("ISCRITTO", "ISTRUTTORE");
        assertThat(JsonPath.<List<String>>read(risposta, "$.content[0].corsi[*].nome")).containsExactly("Pilates");
        assertThat(JsonPath.<List<String>>read(risposta, "$.content[0].corsiInsegnati[*].nome")).containsExactly("Yoga");
    }

    @Test
    @DisplayName("Query in due passi: poche query, indipendenti dal numero di utenti, e nessuna paginazione 'in memoria'")
    void pocheQuery(CapturedOutput log) throws Exception {
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");
        for (Utente u : creaIscritti(30, "Cognome")) {
            u.setCorsi(new HashSet<>(Set.of(yoga, pilates)));
            utenti.save(u);
        }
        ricarica();                              // si svuota la cache: le query devono andare davvero al database

        statistiche().clear();
        mockMvc.perform(get("/api/utenti").param("size", "25").with(comeAdminApi())).andExpect(status().isOk());
        long query = statistiche().getPrepareStatementCount();

        // Conteggio + pagina di utenti + relazioni di quegli utenti = 3. Con una query per utente sarebbero 25 e più.
        assertThat(query).isLessThanOrEqualTo(4);
        // Hibernate scrive questo avviso quando pagina "in memoria" perché la query carica collezioni insieme alla pagina.
        assertThat(log.getAll()).doesNotContain("firstResult/maxResults specified with collection fetch");
    }

    // ------------------------------------------------------------------------- CORSI

    @Test
    @DisplayName("Corsi: pagina, ordinamento per durata, ricerca per nome, istruttore caricato con poche query")
    void corsiPaginati() throws Exception {
        Utente gino = creaUtente("Gino", "Verdi", "gino@example.com", ruolo("ISTRUTTORE"));
        for (int i = 0; i < 25; i++) {
            Corso c = creaCorso(String.format("Corso%02d", i));
            c.setIstruttore(gino);
            corsi.save(c);
        }
        creaCorso("Yoga Serale");
        ricarica();

        statistiche().clear();
        String prima = json("/api/corsi", "size", "10");
        assertThat(statistiche().getPrepareStatementCount()).isLessThanOrEqualTo(3);   // conteggio + pagina (istruttore con JOIN)
        assertThat(JsonPath.<Integer>read(prima, "$.totalElements")).isEqualTo(26);
        assertThat(JsonPath.<String>read(prima, "$.content[0].istruttore.cognome")).isEqualTo("Verdi");

        assertThat(JsonPath.<Integer>read(json("/api/corsi", "q", "yoga"), "$.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(json("/api/corsi", "sort", "nome,desc", "size", "2"), "$.content[*].nome"))
                .containsExactly("Yoga Serale", "Corso24");
    }

    @Test
    @DisplayName("Corsi ordinabili anche per id, crescente e decrescente")
    void corsiOrdinatiPerId() throws Exception {
        Corso primo = creaCorso("Zumba");               // creati in quest'ordine: id crescenti, ma il nome è in ordine inverso
        Corso secondo = creaCorso("Yoga");
        Corso terzo = creaCorso("Aerobica");
        ricarica();

        List<Number> asc = campi(json("/api/corsi", "sort", "id"), "id");
        List<Number> desc = campi(json("/api/corsi", "sort", "id,desc"), "id");

        assertThat(asc).extracting(n -> n.longValue()).containsSubsequence(primo.getId(), secondo.getId(), terzo.getId());
        assertThat(desc).extracting(n -> n.longValue()).containsSubsequence(terzo.getId(), secondo.getId(), primo.getId());
        assertThat(JsonPath.<String>read(json("/api/corsi", "sort", "id,desc"), "$.sort")).isEqualTo("id,desc");
    }

    // ------------------------------------------------------------------ ABBONAMENTI

    @Test
    @DisplayName("Abbonamenti: pagina, ricerca sull'iscritto, ordinamento per iscritto")
    void abbonamentiPaginati() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente laura = creaUtente("Laura", "Bianchi", "laura@example.com", ruolo("ISCRITTO"));
        for (int i = 0; i < 6; i++) creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusMonths(i));
        for (int i = 0; i < 4; i++) creaAbbonamento(laura, TipoAbbonamento.MENSILE, oggi.plusMonths(i));
        ricarica();

        String tutti = json("/api/abbonamenti", "size", "4");
        assertThat(JsonPath.<Integer>read(tutti, "$.totalElements")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(tutti, "$.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<String>read(tutti, "$.sort")).isEqualTo("dataFine,desc");        // ordinamento di partenza

        String soloMario = json("/api/abbonamenti", "q", "rossi");                            // cerca nell'iscritto
        assertThat(JsonPath.<Integer>read(soloMario, "$.totalElements")).isEqualTo(6);
        assertThat(JsonPath.<List<String>>read(soloMario, "$.content[*].utente.cognome")).containsOnly("Rossi");

        String perIscritto = json("/api/abbonamenti", "sort", "iscritto,asc", "size", "4");   // Bianchi prima di Rossi
        assertThat(JsonPath.<List<String>>read(perIscritto, "$.content[*].utente.cognome")).containsOnly("Bianchi");
    }

    @Test
    @DisplayName("Storico di un utente: paginato, dal più recente; la pagina contiene solo i SUOI abbonamenti")
    void storicoPaginato() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente laura = creaUtente("Laura", "Bianchi", "laura@example.com", ruolo("ISCRITTO"));
        for (int i = 0; i < 12; i++) creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusMonths(i));
        creaAbbonamento(laura, TipoAbbonamento.ANNUALE, oggi);
        ricarica();

        String url = "/api/utenti/" + mario.getId() + "/abbonamenti";
        String seconda = json(url, "size", "5", "page", "1");
        assertThat(JsonPath.<Integer>read(seconda, "$.totalElements")).isEqualTo(12);
        assertThat(JsonPath.<Integer>read(seconda, "$.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<List<String>>read(seconda, "$.content[*].utente.cognome")).hasSize(5).containsOnly("Rossi");

        // Dal più recente: la prima pagina inizia dall'abbonamento che parte più avanti nel tempo.
        List<String> inizi = campi(json(url, "size", "12"), "dataInizio");
        assertThat(inizi).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(inizi.get(0)).isEqualTo(oggi.plusMonths(11).toString());

        mockMvc.perform(get(url).param("sort", "utente").with(comeAdminApi())).andExpect(status().isBadRequest());
    }
}
