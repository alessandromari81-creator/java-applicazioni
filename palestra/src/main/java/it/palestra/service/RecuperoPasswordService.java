/*
 * FILE: RecuperoPasswordService.java
 * A COSA SERVE: coordina i tre casi in cui un utente già esistente riceve un link per scegliere la password:
 *   - richiediReset(email): «password dimenticata»;
 *   - invita(utenteId): l'amministratore invita dal pannello un utente creato senza password;
 *   - impostaPassword(token, password): l'utente ha aperto il link e ha scelto la password.
 * richiediReset NON dice mai se l'email esiste: se esiste (e non è appena partita un'altra email) manda il link, altrimenti non fa nulla; la risposta
 * mostrata alla persona la decide il controller ed è sempre la stessa. Quando la password viene impostata si revocano access e refresh token
 * (lo fa UtenteService.cambiaPassword), così chi avesse rubato un accesso lo perde.
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.TipoTokenPassword;
import it.palestra.model.Utente;
import it.palestra.repository.UtenteRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecuperoPasswordService {

    private final UtenteRepository utenti;
    private final TokenPasswordService tokens;
    private final NotificheEmail notifiche;
    private final UtenteService utenteService;

    public RecuperoPasswordService(UtenteRepository utenti, TokenPasswordService tokens, NotificheEmail notifiche,
                                   UtenteService utenteService) {
        this.utenti = utenti;
        this.tokens = tokens;
        this.notifiche = notifiche;
        this.utenteService = utenteService;
    }

    // «Password dimenticata»: se esiste un utente con questa email (senza distinguere maiuscole) e non gli è appena stata spedita un'altra email,
    // emette un link e lo invia. In ogni altro caso non fa nulla e non lo segnala. Non è @Transactional di proposito: il token si salva (e si conferma)
    // PRIMA di spedire l'email, così il link funziona subito.
    public void richiediReset(String email) {
        Optional<Utente> trovato = utenti.findByEmailIgnoreCase(email.trim());
        if (trovato.isEmpty()) {
            return;
        }
        Utente utente = trovato.get();
        if (tokens.inPausa(utente.getId(), TipoTokenPassword.RESET)) {
            return;
        }
        String token = tokens.emetti(utente.getId(), TipoTokenPassword.RESET);
        notifiche.reimpostaPassword(utente.getEmail(), utente.getNome(), token);
    }

    // Invito dell'amministratore: solo per chi NON ha ancora una password. Restituisce l'email a cui è stato inviato. Se l'email non parte lo dice
    // all'amministratore (a differenza di richiediReset, qui chi chiede è un amministratore e deve sapere come è andata).
    public String invita(Long utenteId) {
        Utente utente = utenti.findById(utenteId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + utenteId + " non trovato"));
        if (utente.isConPassword()) {
            throw new RegolaViolataException("Questo utente ha già una password: può usare «Password dimenticata» dalla pagina di accesso");
        }
        String token = tokens.emetti(utente.getId(), TipoTokenPassword.ATTIVAZIONE);
        if (!notifiche.invitoAttivazione(utente.getEmail(), utente.getNome(), token)) {
            throw new RegolaViolataException("L'email di invito non è partita: controlla la configurazione del server di posta");
        }
        return utente.getEmail();
    }

    // L'utente ha aperto il link e scelto la password: si consuma il token (se non è valido: TokenNonValidoException) e si imposta la password.
    // Tutto in una transazione: o si fa tutto o niente.
    @Transactional
    public void impostaPassword(String token, String nuovaPassword) {
        utenteService.erroreLunghezzaPassword(nuovaPassword).ifPresent(messaggio -> {
            throw new RegolaViolataException(messaggio);
        });
        Utente utente = tokens.consuma(token);
        utenteService.cambiaPassword(utente.getEmail(), nuovaPassword);
    }
}
