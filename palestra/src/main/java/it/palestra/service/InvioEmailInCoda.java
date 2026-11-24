/*
 * FILE: InvioEmailInCoda.java
 * A COSA SERVE: la versione di InvioEmail che NON spedisce l'email ma la mette in coda (tabella email_in_coda). NotificheEmail chiama invia() come sempre, ma
 * invece di aspettare il server di posta la richiesta scrive una riga e prosegue: la persona riceve subito la risposta, e se il server di posta è spento
 * l'email non si perde (la riprova SpedizioneEmailProgrammata). È la versione usata dall'applicazione quando app.email.coda.attiva è true (predefinito); con false
 * (e nei test) l'email parte subito, come prima.
 * @Primary: quando ci sono due InvioEmail (questa e quella vera che spedisce, SmtpInvioEmail) chi non specifica altro, cioè NotificheEmail, riceve questa.
 */
package it.palestra.service;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(name = "app.email.coda.attiva", havingValue = "true")
public class InvioEmailInCoda implements InvioEmail {

    private final CodaEmailService coda;
    private final Clock clock;

    public InvioEmailInCoda(CodaEmailService coda, Clock clock) {
        this.coda = coda;
        this.clock = clock;
    }

    // Scrive la riga in coda (spedibile subito) e torna. Se la scrittura nel database fallisce l'eccezione esce, e chi chiama (NotificheEmail) la scrive nel log.
    @Override
    public void invia(String destinatario, String oggetto, String testoHtml, String testoSemplice) {
        coda.accoda(destinatario, oggetto, testoHtml, testoSemplice, clock.instant());
    }
}
