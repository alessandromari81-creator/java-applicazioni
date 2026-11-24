/*
 * FILE: PuliziaProgrammata.java
 * A COSA SERVE: ogni notte cancella dal database i refresh token, i link per la password (token_password) e le registrazioni non confermate (registrazione_in_attesa) scaduti, che altrimenti si accumulerebbero per sempre (ogni login e
 * ogni rinnovo ne crea uno nuovo). Un token scaduto non serve più a nessuno: sarebbe rifiutato comunque.
 * Cancella anche le righe FALLITA della coda delle email più vecchie di app.email.coda.fallite-ore (predefinito 24): contengono il link con il token vero, non devono restare.
 * QUANDO GIRA: secondo l'espressione cron di app.pulizia.cron (predefinita: ogni giorno alle 03:30, formato "secondi minuti ore giorno
 * mese giornoSettimana"). Se l'app è spenta a quell'ora la pulizia salta e riprova la notte dopo.
 * NOTA: se l'app gira in più istanze, ognuna esegue la pulizia (innocuo: è una DELETE che la seconda trova già fatta).
 */
package it.palestra.service;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PuliziaProgrammata {

    private static final Logger LOG = LoggerFactory.getLogger(PuliziaProgrammata.class);

    private final RefreshTokenService refreshTokens;
    private final TokenPasswordService tokenPassword;
    private final RegistrazioneService registrazioni;
    private final CodaEmailService codaEmail;
    private final Clock clock;
    private final Duration vitaFallite;

    public PuliziaProgrammata(RefreshTokenService refreshTokens, TokenPasswordService tokenPassword,
                              RegistrazioneService registrazioni, CodaEmailService codaEmail, Clock clock,
                              @Value("${app.email.coda.fallite-ore:24}") long falliteOre) {
        this.codaEmail = codaEmail;
        this.clock = clock;
        this.vitaFallite = Duration.ofHours(falliteOre);
        this.refreshTokens = refreshTokens;
        this.tokenPassword = tokenPassword;
        this.registrazioni = registrazioni;
    }

    // Eseguito dallo scheduler di Spring secondo il cron. Cancella i refresh token e i link per la password scaduti e scrive nel log quanti (solo se ce n'erano).
    @Scheduled(cron = "${app.pulizia.cron:0 30 3 * * *}")
    public void puliziaRefreshTokenScaduti() {
        int cancellati = refreshTokens.eliminaScaduti();
        if (cancellati > 0) {
            LOG.info("Pulizia notturna: cancellati {} refresh token scaduti", cancellati);
        }
        int linkScaduti = tokenPassword.eliminaScaduti();
        if (linkScaduti > 0) {
            LOG.info("Pulizia notturna: cancellati {} link per la password scaduti", linkScaduti);
        }
        int registrazioniScadute = registrazioni.eliminaScadute();
        if (registrazioniScadute > 0) {
            LOG.info("Pulizia notturna: cancellate {} registrazioni non confermate scadute", registrazioniScadute);
        }
        int emailFallite = codaEmail.eliminaFalliteVecchie(clock.instant(), vitaFallite);
        if (emailFallite > 0) {
            LOG.info("Pulizia notturna: cancellate {} email fallite dalla coda (contenevano un link con il token)", emailFallite);
        }
    }
}
