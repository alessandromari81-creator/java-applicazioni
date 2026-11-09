/*
 * FILE: AbbonamentoApiController.java
 * A COSA SERVE: la API REST degli abbonamenti. Gli abbonamenti "appartengono" a un utente, quindi creazione e storico
 * stanno sotto l'indirizzo dell'utente (come nelle pagine). Stesso AbbonamentoService delle pagine: valgono le stesse
 * regole (solo ISCRITTO, niente passato, niente sovrapposizioni, si eliminano solo i non ancora iniziati).
 *
 * ENDPOINT:
 *   GET    /api/abbonamenti                                   200  tutti gli abbonamenti
 *   GET    /api/abbonamenti/{id}                              200  uno                    | 404
 *   DELETE /api/abbonamenti/{id}                              204  elimina (solo futuri)   | 404 | 409
 *   GET    /api/utenti/{id}/abbonamenti                       200  lo storico di un utente | 404
 *   GET    /api/utenti/{id}/abbonamenti/primo-giorno-libero   200  { "data": ... } per un rinnovo
 *   POST   /api/utenti/{id}/abbonamenti                       201  crea (con Location)    | 400 | 404
 */
package it.palestra.api;

import it.palestra.api.dto.AbbonamentoRequest;
import it.palestra.api.dto.AbbonamentoResponse;
import it.palestra.api.dto.DataResponse;
import it.palestra.model.Abbonamento;
import it.palestra.service.AbbonamentoService;
import it.palestra.web.AbbonamentoForm;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Il prefisso comune è solo /api: gli indirizzi completi sono scritti sui singoli metodi, perché alcuni stanno sotto
// /api/abbonamenti e altri sotto /api/utenti/{id}/abbonamenti.
@RestController
@RequestMapping("/api")
public class AbbonamentoApiController {

    private final AbbonamentoService service;

    public AbbonamentoApiController(AbbonamentoService service) {
        this.service = service;
    }

    @GetMapping("/abbonamenti")
    public List<AbbonamentoResponse> elenco() {
        return come(service.elenco());
    }

    @GetMapping("/abbonamenti/{id}")
    public AbbonamentoResponse dettaglio(@PathVariable Long id) {
        return AbbonamentoResponse.da(service.trova(id), service.oggi());
    }

    // DELETE: solo gli abbonamenti non ancora iniziati; gli altri restano nello storico (RegolaViolata -> 409).
    @DeleteMapping("/abbonamenti/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        service.elimina(id);   // lancia già il 404 se non esiste
    }

    // Lo storico di UN utente, dal più recente. Se l'utente non esiste: 404 (trovaUtente).
    @GetMapping("/utenti/{utenteId}/abbonamenti")
    public List<AbbonamentoResponse> storicoDiUtente(@PathVariable Long utenteId) {
        service.trovaUtente(utenteId);
        return come(service.abbonamentiDi(utenteId));
    }

    // La data da cui l'utente può iniziare un nuovo abbonamento senza sovrapposizioni (oggi, oppure il giorno dopo la
    // scadenza dell'ultimo): serve a un client per proporre la data di un rinnovo.
    @GetMapping("/utenti/{utenteId}/abbonamenti/primo-giorno-libero")
    public DataResponse primoGiornoLibero(@PathVariable Long utenteId) {
        service.trovaUtente(utenteId);
        return new DataResponse(service.primoGiornoLibero(utenteId));
    }

    // POST: crea un abbonamento per l'utente nell'indirizzo. Il corpo ha solo tipo e data di inizio. I controlli:
    //  1) l'utente deve esistere (404) ed essere ISCRITTO (400, campo "utente");
    //  2) la data non può essere nel passato né sovrapporsi ad altri abbonamenti (400, campo "dataInizio").
    @PostMapping("/utenti/{utenteId}/abbonamenti")
    public ResponseEntity<AbbonamentoResponse> crea(@PathVariable Long utenteId,
                                                    @Valid @RequestBody AbbonamentoRequest richiesta) {
        service.trovaUtente(utenteId);

        AbbonamentoForm form = new AbbonamentoForm();
        form.setUtenteId(utenteId);
        form.setTipo(richiesta.tipo());
        form.setDataInizio(richiesta.dataInizio());

        service.erroreSulUtente(form).ifPresent(m -> {
            throw ErroreSuCampoException.su("utente", m);
        });
        service.erroreSullaDataDiInizio(form).ifPresent(m -> {
            throw ErroreSuCampoException.su("dataInizio", m);
        });

        Abbonamento creato = service.crea(form);
        return ResponseEntity.created(URI.create("/api/abbonamenti/" + creato.getId()))
                .body(AbbonamentoResponse.da(service.trova(creato.getId()), service.oggi()));
    }

    // Trasforma una lista di entità in una lista di risposte, calcolando lo stato con la data di oggi.
    private List<AbbonamentoResponse> come(List<Abbonamento> abbonamenti) {
        var oggi = service.oggi();
        return abbonamenti.stream().map(a -> AbbonamentoResponse.da(a, oggi)).toList();
    }
}
