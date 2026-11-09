/*
 * FILE: CorsoApiController.java
 * A COSA SERVE: la API REST dei corsi (/api/corsi): lista, dettaglio, creazione, modifica ed eliminazione, in JSON.
 * Usa gli STESSI service delle pagine (CorsoService): la logica e le regole sono scritte una volta sola, qui cambia
 * solo come si ricevono i dati (JSON) e come si risponde (JSON + codice HTTP).
 *
 * ENDPOINT:
 *   GET    /api/corsi        200  elenco dei corsi
 *   GET    /api/corsi/{id}   200  un corso            | 404 se non esiste
 *   POST   /api/corsi        201  crea (con Location) | 400 dati non validi
 *   PUT    /api/corsi/{id}   200  modifica            | 400 dati non validi | 404
 *   DELETE /api/corsi/{id}   204  elimina             | 404 | 409 se ha iscritti
 */
package it.palestra.api;

import it.palestra.api.dto.CorsoRequest;
import it.palestra.api.dto.CorsoResponse;
import it.palestra.model.Corso;
import it.palestra.service.CorsoService;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import it.palestra.web.CorsoForm;

@RestController
@RequestMapping("/api/corsi")
public class CorsoApiController {

    private final CorsoService service;

    public CorsoApiController(CorsoService service) {
        this.service = service;
    }

    // GET /api/corsi -> elenco in JSON. Ogni Corso diventa un CorsoResponse (il DTO).
    @GetMapping
    public List<CorsoResponse> elenco() {
        return service.elenco().stream().map(CorsoResponse::da).toList();
    }

    // GET /api/corsi/5 -> un corso. Se non esiste il service lancia RisorsaNonTrovataException -> 404 in JSON.
    @GetMapping("/{id}")
    public CorsoResponse dettaglio(@PathVariable Long id) {
        return CorsoResponse.da(service.trova(id));
    }

    // POST /api/corsi -> crea. Risponde 201 Created con l'intestazione "Location" (dove trovare il corso creato) e il
    // corso nel corpo. Il corso si rilegge con trova() per avere l'istruttore già caricato (la risposta lo mostra).
    @PostMapping
    public ResponseEntity<CorsoResponse> crea(@Valid @RequestBody CorsoRequest richiesta) {
        CorsoForm form = richiesta.comeForm();
        controllaIstruttore(form, null);
        Corso creato = service.crea(form);
        return ResponseEntity.created(URI.create("/api/corsi/" + creato.getId()))
                .body(CorsoResponse.da(service.trova(creato.getId())));
    }

    // PUT /api/corsi/5 -> modifica. PUT SOSTITUISCE tutti i campi con quelli inviati (istruttoreId assente = nessun
    // istruttore). Prima si controlla che il corso esista (404), poi le regole sui dati (400).
    @PutMapping("/{id}")
    public CorsoResponse modifica(@PathVariable Long id, @Valid @RequestBody CorsoRequest richiesta) {
        service.trova(id);
        CorsoForm form = richiesta.comeForm();
        controllaIstruttore(form, id);
        service.modifica(id, form);
        return CorsoResponse.da(service.trova(id));
    }

    // DELETE /api/corsi/5 -> elimina. 204 No Content = fatto, niente da restituire. Se ha iscritti il service lancia
    // RegolaViolataException -> 409. trova() prima serve ad avere un 404 se il corso non esiste.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        service.trova(id);
        service.elimina(id);
    }

    // Regola sull'istruttore (ruolo ISTRUTTORE, non iscritto al corso): stessa del form delle pagine, qui l'errore
    // diventa un 400 con il campo "istruttoreId".
    private void controllaIstruttore(CorsoForm form, Long corsoId) {
        service.erroreSullIstruttore(form, corsoId).ifPresent(messaggio -> {
            throw ErroreSuCampoException.su("istruttoreId", messaggio);
        });
    }
}
