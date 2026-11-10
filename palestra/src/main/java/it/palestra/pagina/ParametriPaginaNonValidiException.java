/*
 * FILE: ParametriPaginaNonValidiException.java
 * A COSA SERVE: lanciata quando i parametri di una lista (page, size, sort, q) hanno valori non ammessi. Porta con sé i
 * messaggi per ciascun parametro. La API la trasforma in un 400 in JSON con "campi"; per le pagine HTML
 * @ResponseStatus la fa diventare un 400 con la pagina d'errore standard.
 */
package it.palestra.pagina;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ParametriPaginaNonValidiException extends RuntimeException {

    // Parametro -> messaggi (es. "size" -> ["Deve essere compresa tra 1 e 100"]).
    private final Map<String, List<String>> campi;

    public ParametriPaginaNonValidiException(Map<String, List<String>> campi) {
        super("Parametri di paginazione non validi: " + campi);
        this.campi = campi;
    }

    public Map<String, List<String>> getCampi() { return campi; }
}
