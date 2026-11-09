/*
 * FILE: GestoreErrori.java
 * A COSA SERVE: gestione centralizzata delle eccezioni dei controller. Quando un controller (o un
 * service chiamato da lui) lancia l'eccezione indicata, Spring la passa qui invece di mostrare una
 * pagina di errore 500. Si scrive UNA volta e vale per tutti i controller.
 * PARAGONE LARAVEL: il metodo render()/register() di app/Exceptions/Handler.php.
 *
 * Le eccezioni NON gestite qui (URL inesistente, errori imprevisti) le gestisce Spring Boot con le
 * pagine in templates/error/ e templates/error.html.
 */
package it.palestra.web;

import it.palestra.exception.RisorsaNonTrovataException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ControllerAdvice = "consiglio" che vale per i controller indicati. Con basePackages si limita ai controller delle
// PAGINE (package it.palestra.web): la API REST (package it.palestra.api) ha il suo gestore che risponde in JSON,
// mentre questo risponde con una pagina HTML.
@ControllerAdvice(basePackages = "it.palestra.web")
public class GestoreErrori {

    // @ExceptionHandler: questo metodo viene chiamato quando un controller lancia
    // RisorsaNonTrovataException. L'eccezione arriva come parametro.
    // @ResponseStatus: la risposta HTTP avrà codice 404 (e non 200, che sarebbe sbagliato per
    // una pagina di errore: browser e strumenti come Bruno devono capire che è un errore).
    @ExceptionHandler(RisorsaNonTrovataException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String nonTrovato(RisorsaNonTrovataException e, Model model) {
        // Passiamo il messaggio dell'eccezione alla pagina (es. "Utente 99 non trovato").
        model.addAttribute("dettaglio", e.getMessage());
        // Come in un controller: il nome del template da mostrare (templates/error/404.html).
        return "error/404";
    }
}
