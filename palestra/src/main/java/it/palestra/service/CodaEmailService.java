/*
 * FILE: CodaEmailService.java
 * A COSA SERVE: le operazioni sulla coda delle email, ognuna in una piccola transazione: mettere in coda un'email, leggere le prossime da spedire,
 * «reclamarne» una, segnarla spedita (la riga si cancella) o fallita, rimettere in coda quelle rimaste IN_CORSO troppo a lungo e ripulire le FALLITA vecchie.
 * Le piccole transazioni sono volute: durante l'invio vero (lento, può fallire) non c'è nessuna transazione aperta e nessuna riga bloccata.
 * Chi decide COSA fare e QUANDO è il compito programmato SpedizioneEmailProgrammata; qui c'è solo l'accesso ai dati. L'ora arriva sempre dal chiamante
 * (che la prende dal Clock): così nei test si sceglie l'ora senza fermare nessun orologio.
 */
package it.palestra.service;

import it.palestra.model.EmailInCoda;
import it.palestra.model.StatoEmail;
import it.palestra.repository.EmailInCodaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CodaEmailService {

    private final EmailInCodaRepository coda;

    public CodaEmailService(EmailInCodaRepository coda) {
        this.coda = coda;
    }

    // Mette un'email in coda: DA_INVIARE e spedibile subito. È tutto ciò che fa la richiesta dell'utente al posto di aspettare il server di posta.
    public EmailInCoda accoda(String destinatario, String oggetto, String corpoHtml, String corpoTesto, Instant ora) {
        return coda.save(new EmailInCoda(destinatario, oggetto, corpoHtml, corpoTesto, ora));
    }

    // Le prossime (al massimo "massimo") email da spedire a quest'ora. Solo lettura: non le prenota.
    @Transactional(readOnly = true)
    public List<EmailInCoda> prossime(Instant ora, int massimo) {
        return coda.daSpedire(ora, PageRequest.of(0, massimo));
    }

    // Prova a prendere in carico l'email: true se ora è tua, false se l'ha già presa un'altra istanza (o non è più DA_INVIARE).
    public boolean reclama(Long id, Instant ora) {
        return coda.reclama(id, ora) == 1;
    }

    // L'email è partita: la riga si cancella (contiene il link con il token vero, non deve restare).
    public void segnaSpedita(Long id) {
        coda.deleteById(id);
    }

    // L'invio è fallito: si conta il tentativo e si programma il prossimo, oppure la riga diventa FALLITA (logica in EmailInCoda.registraFallimento).
    // Basta cambiare l'entità: a fine transazione Hibernate scrive le modifiche.
    public void segnaFallita(Long id, Instant ora, int massimoTentativi, String errore) {
        coda.findById(id).ifPresent(e -> e.registraFallimento(ora, massimoTentativi, errore));
    }

    // Le righe IN_CORSO da più di "timeout" tornano DA_INVIARE (il processo che le spediva si è fermato). Restituisce quante.
    public int rimettiInCodaScadute(Instant ora, Duration timeout) {
        return coda.rimettiDaInviare(ora.minus(timeout));
    }

    // Cancella le righe FALLITA più vecchie di "vita" (contengono il link con il token vero). Restituisce quante.
    public int eliminaFalliteVecchie(Instant ora, Duration vita) {
        return coda.eliminaFallitePrima(ora.minus(vita));
    }

    // Quante righe ci sono in un dato stato.
    @Transactional(readOnly = true)
    public long quante(StatoEmail stato) {
        return coda.countByStato(stato);
    }
}
