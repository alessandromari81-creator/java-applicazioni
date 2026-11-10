/*
 * FILE: AbbonamentoApiController.java
 * A COSA SERVE: la API REST degli abbonamenti. Gli abbonamenti "appartengono" a un utente, quindi creazione e storico
 * stanno sotto l'indirizzo dell'utente (come nelle pagine). Stesso AbbonamentoService delle pagine: valgono le stesse
 * regole (solo ISCRITTO, niente passato, niente sovrapposizioni, si eliminano solo i non ancora iniziati).
 *
 * ENDPOINT:
 *   GET    /api/abbonamenti                                   200  UNA PAGINA di abbonamenti (?page&size&sort&q: q cerca l'iscritto)
 *   GET    /api/abbonamenti/{id}                              200  uno                    | 404
 *   DELETE /api/abbonamenti/{id}                              204  elimina (solo futuri)   | 404 | 409
 *   GET    /api/utenti/{id}/abbonamenti                       200  UNA PAGINA dello storico di un utente | 404
 *   GET    /api/utenti/{id}/abbonamenti/primo-giorno-libero   200  { "data": ... } per un rinnovo
 *   POST   /api/utenti/{id}/abbonamenti                       201  crea (con Location)    | 400 | 404
 */
package it.palestra.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import it.palestra.api.dto.AbbonamentoRequest;
import it.palestra.api.dto.AbbonamentoResponse;
import it.palestra.api.dto.DataResponse;
import it.palestra.api.dto.PaginaResponse;
import it.palestra.model.Abbonamento;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.service.AbbonamentoService;
import it.palestra.web.AbbonamentoForm;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Il prefisso comune è solo /api: gli indirizzi completi sono scritti sui singoli metodi, perché alcuni stanno sotto
// /api/abbonamenti e altri sotto /api/utenti/{id}/abbonamenti.
@RestController
@Tag(name = "Abbonamenti", description = "Abbonamenti degli iscritti: elenco, storico per utente, creazione/rinnovo, eliminazione dei non iniziati.")
@RequestMapping("/api")
public class AbbonamentoApiController {

    private final AbbonamentoService service;

    public AbbonamentoApiController(AbbonamentoService service) {
        this.service = service;
    }

    // GET /api/abbonamenti -> UNA PAGINA di abbonamenti. Parametri facoltativi: page (da 0), size (1-100, predefinito 20),
    // sort (dataFine, dataInizio, prezzo, tipo, iscritto; di partenza dataFine,desc), q (testo cercato in nome, cognome o
    // email dell'iscritto). Vedi PaginaResponse.
    @Operation(summary = "Elenco paginato di tutti gli abbonamenti (ricerca q sull'iscritto)")
    @GetMapping("/abbonamenti")
    public PaginaResponse<AbbonamentoResponse> elenco(
            @Parameter(description = "Numero di pagina, parte da 0 (default 0)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (default 20)") @RequestParam(required = false) Integer size,
            @Parameter(description = "Ordinamento: campo oppure campo,asc|desc. Campi ammessi: dataFine (default, decrescente), dataInizio, prezzo, tipo, iscritto") @RequestParam(required = false) String sort,
            @Parameter(description = "Testo da cercare (parziale, senza distinguere maiuscole, max 100 caratteri)") @RequestParam(required = false) String q) {
        RichiestaPagina richiesta = RichiestaPagina.da(page, size, sort, q, OrdinamentiListe.ABBONAMENTI);
        return pagina(service.elenco(richiesta), richiesta);
    }

    @Operation(summary = "Dettaglio di un abbonamento")
    @GetMapping("/abbonamenti/{id}")
    public AbbonamentoResponse dettaglio(@PathVariable Long id) {
        return AbbonamentoResponse.da(service.trova(id), service.oggi());
    }

    // DELETE: solo gli abbonamenti non ancora iniziati; gli altri restano nello storico (RegolaViolata -> 409).
    @Operation(summary = "Elimina un abbonamento non ancora iniziato")
    @DeleteMapping("/abbonamenti/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        service.elimina(id);   // lancia già il 404 se non esiste
    }

    // UNA PAGINA dello storico di UN utente (di partenza dal più recente). Se l'utente non esiste: 404 (trovaUtente).
    // Parametri: page, size, sort (dataInizio, dataFine, prezzo, tipo). La ricerca q non si applica allo storico.
    @Operation(summary = "Storico paginato degli abbonamenti di un utente, dal più recente")
    @GetMapping("/utenti/{utenteId}/abbonamenti")
    public PaginaResponse<AbbonamentoResponse> storicoDiUtente(
            @PathVariable Long utenteId,
            @Parameter(description = "Numero di pagina, parte da 0 (default 0)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (default 20)") @RequestParam(required = false) Integer size,
            @Parameter(description = "Ordinamento: campo oppure campo,asc|desc. Campi ammessi: dataInizio (default, decrescente), dataFine, prezzo, tipo") @RequestParam(required = false) String sort) {
        service.trovaUtente(utenteId);
        RichiestaPagina richiesta = RichiestaPagina.da(page, size, sort, null, OrdinamentiListe.ABBONAMENTI_DI_UTENTE);
        return pagina(service.abbonamentiDi(utenteId, richiesta), richiesta);
    }

    // La data da cui l'utente può iniziare un nuovo abbonamento senza sovrapposizioni (oggi, oppure il giorno dopo la
    // scadenza dell'ultimo): serve a un client per proporre la data di un rinnovo.
    @Operation(summary = "Primo giorno libero per un nuovo abbonamento/rinnovo")
    @GetMapping("/utenti/{utenteId}/abbonamenti/primo-giorno-libero")
    public DataResponse primoGiornoLibero(@PathVariable Long utenteId) {
        service.trovaUtente(utenteId);
        return new DataResponse(service.primoGiornoLibero(utenteId));
    }

    // POST: crea un abbonamento per l'utente nell'indirizzo. Il corpo ha solo tipo e data di inizio. I controlli:
    //  1) l'utente deve esistere (404) ed essere ISCRITTO (400, campo "utente");
    //  2) la data non può essere nel passato né sovrapporsi ad altri abbonamenti (400, campo "dataInizio").
    @Operation(summary = "Crea un abbonamento (o rinnovo) per un utente")
    @ApiResponse(responseCode = "201", description = "Creato: l'indirizzo della nuova risorsa è nell'intestazione Location",
            content = @Content(schema = @Schema(implementation = AbbonamentoResponse.class)))
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

    // Trasforma una pagina di entità in una pagina di risposte, calcolando lo stato con la data di oggi.
    private PaginaResponse<AbbonamentoResponse> pagina(Page<Abbonamento> pagina, RichiestaPagina richiesta) {
        var oggi = service.oggi();
        return PaginaResponse.da(pagina, abbonamento -> AbbonamentoResponse.da(abbonamento, oggi), richiesta);
    }
}
