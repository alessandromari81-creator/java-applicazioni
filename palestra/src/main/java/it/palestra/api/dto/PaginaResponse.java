/*
 * FILE: PaginaResponse.java
 * A COSA SERVE: la forma di TUTTE le liste della API: gli elementi della pagina + i dati per navigare. Esempio:
 *   { "content": [ {...}, {...} ],      gli elementi di questa pagina
 *     "number": 0,                      numero di questa pagina (la prima è 0)
 *     "size": 20,                       elementi per pagina richiesti
 *     "totalElements": 57,              quanti ce ne sono in tutto (con l'eventuale ricerca)
 *     "totalPages": 3,                  quante pagine
 *     "first": true, "last": false,     se è la prima / l'ultima
 *     "sort": "cognome,asc",            ordinamento applicato
 *     "q": "rossi" }                    ricerca applicata (assente se non c'è)
 * Un frontend con questi dati può disegnare la paginazione senza altre chiamate.
 * PARAGONE LARAVEL: il JSON di ->paginate() (data, current_page, per_page, total, last_page...).
 */
package it.palestra.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.palestra.pagina.RichiestaPagina;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

// Il campo "q" null non compare nel JSON.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginaResponse<T>(List<T> content, int number, int size, long totalElements, int totalPages,
                                boolean first, boolean last, String sort, String q) {

    // Costruisce la risposta da una pagina di entità, convertendo ogni elemento nel suo DTO con "conversione".
    public static <E, T> PaginaResponse<T> da(Page<E> pagina, Function<E, T> conversione, RichiestaPagina richiesta) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversione).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages(),
                pagina.isFirst(), pagina.isLast(), richiesta.sortComeTesto(), richiesta.q());
    }
}
