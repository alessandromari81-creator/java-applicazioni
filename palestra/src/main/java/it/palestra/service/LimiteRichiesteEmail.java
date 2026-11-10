/*
 * FILE: LimiteRichiesteEmail.java
 * A COSA SERVE: il limite alle richieste che fanno partire un'EMAIL (registrazione, password dimenticata): senza, il sito potrebbe essere usato per
 * riempire di messaggi la casella di qualcuno. Funziona come LimiteTentativi, con contatori SEPARATI da quelli dei login: ogni richiesta conta (non
 * solo quelle fallite). Soglie da application.properties: app.sicurezza.email-max-richieste (per coppia IP+email, predefinito 5),
 * app.sicurezza.email-max-richieste-per-ip (20) e app.sicurezza.email-finestra-minuti (60).
 * Quando il limite scatta le pagine NON lo dicono: rispondono come sempre ("se l'indirizzo esiste riceverai un messaggio") ma non inviano nulla.
 */
package it.palestra.service;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LimiteRichiesteEmail extends LimiteTentativi {

    public LimiteRichiesteEmail(Clock clock,
                                @Value("${app.sicurezza.email-max-richieste:5}") int maxPerCoppia,
                                @Value("${app.sicurezza.email-max-richieste-per-ip:20}") int maxPerIp,
                                @Value("${app.sicurezza.email-finestra-minuti:60}") long finestraMinuti) {
        super(clock, maxPerCoppia, maxPerIp, finestraMinuti);
    }
}
