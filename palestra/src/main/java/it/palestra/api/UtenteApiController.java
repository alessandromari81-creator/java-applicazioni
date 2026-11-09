/*
 * FILE: UtenteApiController.java
 * A COSA SERVE: la API REST degli utenti (/api/utenti): lista, dettaglio, creazione, modifica ed eliminazione. Usa gli
 * stessi service delle pagine, quindi valgono le stesse regole: ultimo admin protetto, istruttori, abbonamenti, password.
 *
 * ENDPOINT:
 *   GET    /api/utenti        200  elenco (con ruoli, corsi frequentati e corsi tenuti)
 *   GET    /api/utenti/{id}   200  un utente                 | 404
 *   POST   /api/utenti        201  crea                      | 400 dati non validi
 *   PUT    /api/utenti/{id}   200  modifica (sostituisce)    | 400 | 404 | 409 (regole su ruoli)
 *   DELETE /api/utenti/{id}   204  elimina                   | 404 | 409 (ultimo admin, istruttore di corsi, abbonamenti)
 * L'hash della password non esce mai: la risposta ha solo "haPassword".
 */
package it.palestra.api;

import it.palestra.api.dto.UtenteRequest;
import it.palestra.api.dto.UtenteResponse;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.service.UtenteService;
import it.palestra.web.UtenteForm;
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
@RequestMapping("/api/utenti")
public class UtenteApiController {

    private final UtenteService service;

    public UtenteApiController(UtenteService service) {
        this.service = service;
    }

    @GetMapping
    public List<UtenteResponse> elenco() {
        return service.elenco().stream().map(UtenteResponse::da).toList();
    }

    @GetMapping("/{id}")
    public UtenteResponse dettaglio(@PathVariable Long id) {
        return UtenteResponse.da(service.trova(id));
    }

    // POST /api/utenti -> crea. 201 + Location + utente creato. Le regole sui dati (email già usata, password, ruoli,
    // corsi) si controllano tutte insieme, così il client riceve l'elenco completo degli errori in una sola risposta.
    @PostMapping
    public ResponseEntity<UtenteResponse> crea(@Valid @RequestBody UtenteRequest richiesta) {
        UtenteForm form = comeForm(richiesta);
        controllaRegole(form, null);
        Utente creato = service.crea(form);
        return ResponseEntity.created(URI.create("/api/utenti/" + creato.getId()))
                .body(UtenteResponse.da(service.trova(creato.getId())));
    }

    // PUT /api/utenti/5 -> modifica. SOSTITUISCE ruoli e corsi con quelli inviati; la password, se omessa, resta
    // invariata. 404 se non esiste, 400 per dati non validi, 409 se una regola lo impedisce (es. togliere ADMIN
    // all'ultimo admin).
    @PutMapping("/{id}")
    public UtenteResponse modifica(@PathVariable Long id, @Valid @RequestBody UtenteRequest richiesta) {
        service.trova(id);
        UtenteForm form = comeForm(richiesta);
        controllaRegole(form, id);
        service.modifica(id, form);
        return UtenteResponse.da(service.trova(id));
    }

    // DELETE /api/utenti/5 -> 204. Il service rifiuta (409) se è l'ultimo admin, se tiene corsi o se ha abbonamenti.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable Long id) {
        service.elimina(id);   // lancia già il 404 se l'utente non esiste
    }

    // Trasforma la richiesta nel "form" che il service già conosce. I ruoli arrivano per NOME ("ADMIN") e il service li
    // vuole per id: qui si traducono. Un nome sconosciuto è un errore sul campo "ruoli" (con l'elenco di quelli validi).
    private UtenteForm comeForm(UtenteRequest r) {
        Map<String, Long> idPerNome = service.tuttiIRuoli().stream()
                .collect(Collectors.toMap(Ruolo::getNome, Ruolo::getId));

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

        UtenteForm form = new UtenteForm();
        form.setNome(r.nome());
        form.setCognome(r.cognome());
        form.setEmail(r.email());
        form.setPassword(r.password());
        form.setRuoliIds(new ArrayList<>(ruoliIds));
        form.setCorsiIds(new ArrayList<>(r.corsiIdsOVuota()));
        return form;
    }

    // Le regole sui dati che nelle pagine sono errori sui campi: email già usata, password, istruttore iscritto al
    // proprio corso. Si raccolgono tutte e, se ce ne sono, si risponde 400 con "campi".
    private void controllaRegole(UtenteForm form, Long id) {
        Map<String, List<String>> campi = new LinkedHashMap<>();
        if (service.emailInUso(form.getEmail().trim(), id)) {
            campi.put("email", List.of("Email già registrata"));
        }
        service.erroreSullaPassword(form, id).ifPresent(m -> campi.put("password", List.of(m)));
        service.erroreSuiCorsiScelti(form, id).ifPresent(m -> campi.put("corsiIds", List.of(m)));
        if (!campi.isEmpty()) {
            throw new ErroreSuCampoException(campi);
        }
    }
}
