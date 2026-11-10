/*
 * FILE: GestoreErroriApi.java
 * A COSA SERVE: trasforma le eccezioni dei controller della API in risposte JSON con il codice HTTP giusto. È il
 * corrispondente "REST" di GestoreErrori (che invece risponde con pagine HTML per i controller delle pagine).
 * PARAGONE LARAVEL: il metodo render() di Handler.php per le richieste che vogliono JSON.
 *
 * I CODICI USATI (la regola è sempre la stessa, così la API è prevedibile):
 *   400 Bad Request  = i DATI inviati sono sbagliati: formato, campi obbligatori, regole sui campi (email già usata,
 *                      password corta, data nel passato, sovrapposizione...). Quando riguarda dei campi c'è "campi".
 *   404 Not Found    = la risorsa richiesta non esiste.
 *   409 Conflict     = i dati sono corretti ma l'OPERAZIONE è rifiutata da una regola di business sullo stato attuale
 *                      (ultimo admin, corso con iscritti, abbonamento già iniziato...).
 *   401 / 403        = non autenticato / senza permessi: li risponde la catena di sicurezza (SecurityConfig).
 * Non c'è mai una pagina HTML e non vengono mai mostrati stack trace o dettagli interni.
 */
package it.palestra.api;

import it.palestra.api.dto.ErroreApi;
import it.palestra.exception.RefreshTokenNonValidoException;
import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.pagina.ParametriPaginaNonValidiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// @RestControllerAdvice = @ControllerAdvice + @ResponseBody: i metodi restituiscono oggetti che diventano JSON.
// basePackages limita il "consiglio" ai controller della API (package it.palestra.api).
@RestControllerAdvice(basePackages = "it.palestra.api")
public class GestoreErroriApi {

    // 404: il service ha lanciato RisorsaNonTrovataException (corso, utente, abbonamento inesistente).
    @ExceptionHandler(RisorsaNonTrovataException.class)
    ResponseEntity<ErroreApi> nonTrovato(RisorsaNonTrovataException e) {
        return risposta(HttpStatus.NOT_FOUND, "Non trovato", e.getMessage(), null);
    }

    // 409: una regola di business rifiuta l'operazione (il messaggio spiega quale).
    @ExceptionHandler(RegolaViolataException.class)
    ResponseEntity<ErroreApi> regolaViolata(RegolaViolataException e) {
        return risposta(HttpStatus.CONFLICT, "Operazione non consentita", e.getMessage(), null);
    }

    // 400: regole sui campi controllate "a mano" nei controller (vedi ErroreSuCampoException).
    @ExceptionHandler(ErroreSuCampoException.class)
    ResponseEntity<ErroreApi> erroreSuCampi(ErroreSuCampoException e) {
        return risposta(HttpStatus.BAD_REQUEST, "Dati non validi", "Alcuni campi non sono validi", e.getCampi());
    }

    // 400: parametri di una lista (page, size, sort, q) con valori non ammessi. I messaggi dicono quali e perché.
    @ExceptionHandler(ParametriPaginaNonValidiException.class)
    ResponseEntity<ErroreApi> parametriDiLista(ParametriPaginaNonValidiException e) {
        return risposta(HttpStatus.BAD_REQUEST, "Parametri non validi",
                "Alcuni parametri della richiesta non sono validi", e.getCampi());
    }

    // 400: la validazione automatica (@Valid sul corpo della richiesta) ha trovato dei campi sbagliati.
    // Raccoglie gli errori per campo: { "nome": ["Il nome è obbligatorio"], ... }.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroreApi> validazione(MethodArgumentNotValidException e) {
        Map<String, List<String>> campi = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(errore ->
                campi.computeIfAbsent(errore.getField(), k -> new ArrayList<>()).add(errore.getDefaultMessage()));
        return risposta(HttpStatus.BAD_REQUEST, "Dati non validi", "Alcuni campi non sono validi", campi);
    }

    // 400: il corpo della richiesta non è un JSON valido, manca, o contiene un valore impossibile (un tipo che non
    // esiste, una data scritta male). Il testo è generico: i dettagli della libreria sono tecnici e non vanno mostrati.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroreApi> corpoNonLeggibile(HttpMessageNotReadableException e) {
        return risposta(HttpStatus.BAD_REQUEST, "Richiesta non valida",
                "Corpo della richiesta mancante o non valido: controlla il formato JSON, le date (aaaa-mm-gg) "
                        + "e i valori ammessi", null);
    }

    // 400: un parametro dell'indirizzo ha il tipo sbagliato (es. /api/corsi/abc invece di un numero).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErroreApi> parametroNonValido(MethodArgumentTypeMismatchException e) {
        return risposta(HttpStatus.BAD_REQUEST, "Richiesta non valida",
                "Il parametro '" + e.getName() + "' non ha un valore valido", null);
    }

    // 401: credenziali sbagliate al login (lanciata da AutenticazioneService). Messaggio uguale per email inesistente e
    // password errata. Nota: le eccezioni di autenticazione dei FILTRI non passano da qui: le gestisce SecurityConfig.
    // Per il refresh token il messaggio è un altro, ma sempre generico (il motivo preciso è solo nel log del server).
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErroreApi> credenzialiNonValide(AuthenticationException e) {
        String messaggio = e instanceof RefreshTokenNonValidoException
                ? "Refresh token non valido o scaduto: rifai il login"
                : "Credenziali non valide";
        return risposta(HttpStatus.UNAUTHORIZED, "Non autenticato", messaggio, null);
    }

    private ResponseEntity<ErroreApi> risposta(HttpStatus stato, String errore, String messaggio,
                                               Map<String, List<String>> campi) {
        return ResponseEntity.status(stato).body(new ErroreApi(stato.value(), errore, messaggio, campi));
    }
}
