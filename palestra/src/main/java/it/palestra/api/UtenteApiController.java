/*
 * FILE: UtenteApiController.java
 * A COSA SERVE: la API REST degli utenti (/api/utenti): lista, dettaglio, creazione, modifica ed eliminazione. Usa gli
 * stessi service delle pagine, quindi valgono le stesse regole: ultimo admin protetto, istruttori, abbonamenti, password.
 *
 * ENDPOINT:
 *   GET    /api/utenti        200  UNA PAGINA di utenti (con ruoli, corsi frequentati e corsi tenuti)
 *                                   ?page=0&size=20&sort=cognome,asc&q=testo (cerca in nome, cognome, email)
 *   GET    /api/utenti/{id}   200  un utente                 | 404
 *   POST   /api/utenti        201  crea                      | 400 dati non validi
 *   PUT    /api/utenti/{id}   200  modifica (sostituisce)    | 400 | 404 | 409 (regole su ruoli)
 *   DELETE /api/utenti/{id}   204  elimina                   | 404 | 409 (ultimo admin, istruttore di corsi, abbonamenti)
 * L'hash della password non esce mai: la risposta ha solo "haPassword".
 */
package it.palestra.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import it.palestra.api.dto.PaginaResponse;
import it.palestra.api.dto.UtenteRequest;
import it.palestra.api.dto.UtenteResponse;
import it.palestra.model.Utente;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.service.DatiUtente;
import it.palestra.service.UtenteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Utenti", description = "CRUD degli utenti: ruoli (ADMIN, ISTRUTTORE, ISCRITTO), corsi frequentati e corsi tenuti.")
@RequestMapping("/api/utenti")
public class UtenteApiController {

    private final UtenteService service;

    public UtenteApiController(UtenteService service) {
        this.service = service;
    }

    // GET /api/utenti -> UNA PAGINA di utenti. Parametri facoltativi: page (da 0), size (1-100, predefinito 20), sort
    // (cognome, nome, email; es. "email,desc"), q (testo cercato in nome, cognome o email). Vedi PaginaResponse.
    @Operation(summary = "Elenco paginato degli utenti (ricerca q su nome, cognome, email)")
    @GetMapping
    public PaginaResponse<UtenteResponse> elenco(
            @Parameter(description = "Numero di pagina, parte da 0 (default 0)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (default 20)") @RequestParam(required = false) Integer size,
            @Parameter(description = "Ordinamento: campo oppure campo,asc|desc. Campi ammessi: cognome (default), nome, email") @RequestParam(required = false) String sort,
            @Parameter(description = "Testo da cercare (parziale, senza distinguere maiuscole, max 100 caratteri)") @RequestParam(required = false) String q) {
        RichiestaPagina richiesta = RichiestaPagina.da(page, size, sort, q, OrdinamentiListe.UTENTI);
        return PaginaResponse.da(service.elenco(richiesta), utente -> UtenteResponse.da(utente), richiesta);
    }

    @Operation(summary = "Dettaglio di un utente")
    @GetMapping("/{id}")
    public UtenteResponse dettaglio(@PathVariable Long id) {
        return UtenteResponse.da(service.trova(id));
    }

    // POST /api/utenti -> crea. 201 + Location + utente creato. Le regole sui dati (email già usata, password, ruoli,
    // corsi) si controllano tutte insieme, così il client riceve l'elenco completo degli errori in una sola risposta.
    @Operation(summary = "Crea un utente")
    @ApiResponse(responseCode = "201", description = "Creato: l'indirizzo della nuova risorsa è nell'intestazione Location",
            content = @Content(schema = @Schema(implementation = UtenteResponse.class)))
    @PostMapping
    public ResponseEntity<UtenteResponse> crea(@Valid @RequestBody UtenteRequest richiesta) {
        DatiUtente dati = comeDati(richiesta);
        controllaRegole(dati, null);
        Utente creato = service.crea(dati);
        return ResponseEntity.created(URI.create("/api/utenti/" + creato.getId()))
                .body(UtenteResponse.da(service.trova(creato.getId())));
    }

    // PUT /api/utenti/5 -> modifica. SOSTITUISCE ruoli e corsi con quelli inviati; la password, se omessa, resta
    // invariata. 404 se non esiste, 400 per dati non validi, 409 se una regola lo impedisce (es. togliere ADMIN
    // all'ultimo admin).
    @Operation(summary = "Modifica un utente (PUT sostituisce ruoli e corsi)")
    @PutMapping("/{id}")
    public UtenteResponse modifica(@PathVariable Long id, @Valid @RequestBody UtenteRequest richiesta) {
        service.trova(id);
        DatiUtente dati = comeDati(richiesta);
        controllaRegole(dati, id);
        service.modifica(id, dati);
        return UtenteResponse.da(service.trova(id));
    }

    // DELETE /api/utenti/5 -> 204. Il service rifiuta (409) se è l'ultimo admin, se tiene corsi o se ha abbonamenti.
    @Operation(summary = "Elimina un utente (409: ultimo admin, istruttore di corsi, ha abbonamenti)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        service.elimina(id);   // lancia già il 404 se l'utente non esiste
    }

    // Trasforma la richiesta nei dati che il service si aspetta (DatiUtente). I ruoli arrivano per NOME ("ADMIN") e il service li
    // vuole per id: qui si traducono. Un nome sconosciuto è un errore sul campo "ruoli" (con l'elenco di quelli validi).
    private DatiUtente comeDati(UtenteRequest r) {
        Map<String, Long> idPerNome = service.tuttiIRuoli().stream()
                .collect(Collectors.toMap(ruolo -> ruolo.getNome(), ruolo -> ruolo.getId()));

        LinkedHashSet<Long> ruoliIds = new LinkedHashSet<>();   // Set: niente doppioni se un ruolo è ripetuto
        List<String> sconosciuti = new ArrayList<>();
        for (String nome : r.ruoliOVuota()) {
            Long id = nome == null ? null : idPerNome.get(nome.trim().toUpperCase());
            if (id == null) sconosciuti.add(String.valueOf(nome)); else ruoliIds.add(id);
        }
        if (!sconosciuti.isEmpty()) {
            throw ErroreSuCampoException.su("ruoli", "Ruoli sconosciuti: " + sconosciuti
                    + ". Valori ammessi: " + idPerNome.keySet().stream().sorted().toList());
        }

        return new DatiUtente(r.nome(), r.cognome(), r.email(), r.password(), r.indirizzo(), r.cap(), r.citta(),
                new ArrayList<>(ruoliIds), new ArrayList<>(r.corsiIdsOVuota()));
    }

    // Le regole sui dati che nelle pagine sono errori sui campi: email già usata, password, istruttore iscritto al
    // proprio corso. Si raccolgono tutte e, se ce ne sono, si risponde 400 con "campi".
    private void controllaRegole(DatiUtente dati, Long id) {
        Map<String, List<String>> campi = new LinkedHashMap<>();
        if (service.emailInUso(dati.email().trim(), id)) {
            campi.put("email", List.of("Email già registrata"));
        }
        service.erroreSullaPassword(dati, id).ifPresent(m -> campi.put("password", List.of(m)));
        service.erroreSuiCorsiScelti(dati, id).ifPresent(m -> campi.put("corsiIds", List.of(m)));
        if (!campi.isEmpty()) {
            throw new ErroreSuCampoException(campi);
        }
    }
}
