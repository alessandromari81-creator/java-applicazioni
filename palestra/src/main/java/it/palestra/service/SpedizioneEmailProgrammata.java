/*
 * FILE: SpedizioneEmailProgrammata.java
 * A COSA SERVE: il «lavoratore» della coda delle email. Ogni pochi secondi (app.email.coda.intervallo-ms, predefinito 5000) fa un GIRO:
 *   1) rimette DA_INVIARE le email rimaste IN_CORSO troppo a lungo (il giro che le spediva si è fermato);
 *   2) legge le prossime email da spedire (al massimo app.email.coda.gruppo, predefinito 20);
 *   3) per ognuna la «reclama» (UPDATE condizionato: se un'altra istanza dell'app l'ha già presa, la salta) e la spedisce con il server di posta vero;
 *   4) se parte, cancella la riga; se fallisce, conta il tentativo e programma il prossimo con un'attesa crescente (1, 2, 4... minuti), fino a
 *      app.email.coda.max-tentativi (predefinito 5), poi la riga diventa FALLITA.
 * L'invio è fuori da ogni transazione: mentre si aspetta il server di posta non c'è nessuna riga bloccata nel database.
 * GARANZIA: ogni email parte AL MENO una volta. Se l'app si ferma dopo che l'email è partita ma prima di cancellare la riga, dopo il timeout la riga si riprende e l'email
 * può arrivare due volte: è il prezzo di non perderne mai. (Un lavoro con questa garanzia va scritto in modo che rifarlo non faccia danni: qui il peggio è un doppione.)
 * PIÙ ISTANZE: ognuna esegue i suoi giri; la «reclamazione» garantisce che una riga venga spedita da una sola.
 * SI ACCENDE solo se app.email.coda.attiva è true. Come tutti i compiti programmati gira solo se è attivo @EnableScheduling (SchedulazioneConfig, app.pulizia.attiva).
 */
package it.palestra.service;

import it.palestra.model.EmailInCoda;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.coda.attiva", havingValue = "true")
public class SpedizioneEmailProgrammata {

    private static final Logger LOG = LoggerFactory.getLogger(SpedizioneEmailProgrammata.class);

    // Lunghezza massima del motivo dell'errore che si salva (il resto si taglia in EmailInCoda).
    private static final int LUNGHEZZA_MESSAGGIO = 150;

    private final CodaEmailService coda;
    private final InvioEmail invio;
    private final Clock clock;
    private final int gruppo;
    private final int massimoTentativi;
    private final Duration timeoutInCorso;

    // @Qualifier("invioReale"): tra le InvioEmail serve quella che spedisce DAVVERO (SmtpInvioEmail, o EmailFinte nei test), non quella che mette in coda.
    public SpedizioneEmailProgrammata(CodaEmailService coda, @Qualifier("invioReale") InvioEmail invio, Clock clock,
                                      @Value("${app.email.coda.gruppo:20}") int gruppo,
                                      @Value("${app.email.coda.max-tentativi:5}") int massimoTentativi,
                                      @Value("${app.email.coda.in-corso-minuti:10}") long minutiInCorso) {
        this.coda = coda;
        this.invio = invio;
        this.clock = clock;
        this.gruppo = gruppo;
        this.massimoTentativi = massimoTentativi;
        this.timeoutInCorso = Duration.ofMinutes(minutiInCorso);
    }

    // Il giro programmato. fixedDelay = aspetta N millisecondi DOPO la fine del giro precedente (due giri non si sovrappongono mai, nemmeno se uno è lento).
    @Scheduled(fixedDelayString = "${app.email.coda.intervallo-ms:5000}")
    public void eseguiGiro() {
        eseguiGiroAlle(clock.instant());
    }

    // Un giro all'ora indicata; restituisce quante email sono partite. Public perché i test lo chiamano a mano scegliendo l'ora (con il compito programmato spento).
    public int eseguiGiroAlle(Instant ora) {
        int rimesse = coda.rimettiInCodaScadute(ora, timeoutInCorso);
        if (rimesse > 0) {
            LOG.warn("Coda email: {} email erano rimaste IN_CORSO da troppo tempo e sono tornate da inviare", rimesse);
        }
        int spedite = 0;
        for (EmailInCoda e : coda.prossime(ora, gruppo)) {
            if (!coda.reclama(e.getId(), ora)) {
                continue;                                   // un'altra istanza l'ha già presa: si passa alla prossima
            }
            try {
                invio.invia(e.getDestinatario(), e.getOggetto(), e.getCorpoHtml(), e.getCorpoTesto());
                coda.segnaSpedita(e.getId());
                spedite++;
            } catch (RuntimeException errore) {
                // Nel log solo il numero della riga e il tipo di errore: mai il contenuto dell'email, che contiene il link con il token.
                LOG.warn("Coda email: invio dell'email n. {} non riuscito ({}), si riprova più tardi", e.getId(), errore.getClass().getSimpleName());
                coda.segnaFallita(e.getId(), ora, massimoTentativi, descrizione(errore));
            }
        }
        return spedite;
    }

    // Il motivo dell'errore da salvare nella riga: il tipo e un messaggio breve (il messaggio di un errore di posta non contiene il corpo dell'email).
    private String descrizione(RuntimeException errore) {
        String messaggio = errore.getMessage() == null ? "" : ": " + errore.getMessage();
        String testo = errore.getClass().getSimpleName() + messaggio;
        return testo.substring(0, Math.min(testo.length(), LUNGHEZZA_MESSAGGIO));
    }
}
