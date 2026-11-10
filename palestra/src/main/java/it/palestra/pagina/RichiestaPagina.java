/*
 * FILE: RichiestaPagina.java
 * A COSA SERVE: trasforma i parametri GREZZI di una lista (page, size, sort, q) in una richiesta VALIDATA e pronta per il
 * database. Qui sta la regola di tutta la paginazione, per pagine HTML e API:
 *   - page   numero di pagina, da 0 (la prima). Predefinito 0.
 *   - size   quanti elementi per pagina, da 1 a 100. Predefinito 20.
 *   - sort   "campo" oppure "campo,asc" / "campo,desc". Solo i campi ammessi per quella lista (vedi Ordinamenti).
 *   - q      testo da cercare (massimo 100 caratteri); vuoto = nessuna ricerca.
 * Valori non validi NON vengono corretti in silenzio: si lancia un errore (400) che dice quali parametri sono sbagliati.
 * È un "record": una classe di soli dati, immutabile.
 */
package it.palestra.pagina;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

public record RichiestaPagina(int page, int size, String campo, Sort.Direction direzione, String q,
                              String sortEsplicito, Pageable pageable) {

    public static final int SIZE_PREDEFINITA = 20;
    public static final int SIZE_MASSIMA = 100;
    public static final int Q_MASSIMA = 100;

    // Costruisce la richiesta dai parametri grezzi (null = non indicato). "sortEsplicito" resta null se il client non ha
    // scelto un ordinamento: serve a non ripeterlo nei link quando si usa quello di partenza.
    public static RichiestaPagina da(Integer page, Integer size, String sort, String q, Ordinamenti ordinamenti) {
        Map<String, List<String>> errori = new LinkedHashMap<>();

        int pagina = page == null ? 0 : page;
        if (pagina < 0) errori.put("page", List.of("Deve essere 0 o maggiore"));

        int dimensione = size == null ? SIZE_PREDEFINITA : size;
        if (dimensione < 1 || dimensione > SIZE_MASSIMA) {
            errori.put("size", List.of("Deve essere compresa tra 1 e " + SIZE_MASSIMA));
        }

        String campo = ordinamenti.predefinito();
        Sort.Direction direzione = ordinamenti.direzionePredefinita();
        String sortEsplicito = null;
        if (sort != null && !sort.isBlank()) {
            String[] parti = sort.trim().split(",", -1);             // "cognome,desc" -> ["cognome", "desc"]
            String nome = parti[0].trim();
            Optional<Sort.Direction> dir = parti.length == 1
                    ? Optional.of(Sort.Direction.ASC)                // senza direzione: crescente
                    : Sort.Direction.fromOptionalString(parti[1].trim());
            if (parti.length > 2 || !ordinamenti.ammette(nome) || dir.isEmpty()) {
                errori.put("sort", List.of("Valore non valido. Formato: campo oppure campo,asc|desc. Campi ammessi: "
                        + ordinamenti.nomi()));
            } else {
                campo = nome;
                direzione = dir.get();
                sortEsplicito = nome + "," + direzione.name().toLowerCase();
            }
        }

        String testo = q == null ? null : q.trim();
        if (testo != null && testo.isEmpty()) testo = null;          // "   " = nessuna ricerca
        if (testo != null && testo.length() > Q_MASSIMA) {
            errori.put("q", List.of("Massimo " + Q_MASSIMA + " caratteri"));
        }

        if (!errori.isEmpty()) throw new ParametriPaginaNonValidiException(errori);

        return new RichiestaPagina(pagina, dimensione, campo, direzione, testo, sortEsplicito,
                PageRequest.of(pagina, dimensione, ordinamenti.sort(campo, direzione)));
    }

    // L'ordinamento in forma di testo ("cognome,asc"), come lo scrive il client.
    public String sortComeTesto() {
        return campo + "," + direzione.name().toLowerCase();
    }

    // La parte "?page=2&size=50&sort=cognome,desc&q=rossi" per i link, scrivendo solo i valori NON predefiniti (gli indirizzi
    // restano corti). Il testo viene codificato (spazi, &, accenti...), così non può rompere né manipolare l'indirizzo.
    // paginaScelta/sortScelto permettono di costruire il link a un'altra pagina o a un altro ordinamento tenendo il resto.
    public String query(int paginaScelta, String sortScelto) {
        return query(paginaScelta, sortScelto, true);
    }

    // Come sopra, con la possibilità di NON includere la ricerca (serve al link "azzera la ricerca").
    public String query(int paginaScelta, String sortScelto, boolean conRicerca) {
        UriComponentsBuilder b = UriComponentsBuilder.newInstance();
        if (paginaScelta > 0) b.queryParam("page", paginaScelta);
        if (size != SIZE_PREDEFINITA) b.queryParam("size", size);
        if (sortScelto != null) b.queryParam("sort", sortScelto);
        if (conRicerca && q != null) b.queryParam("q", q);
        return b.build().encode().toUriString();
    }
}
