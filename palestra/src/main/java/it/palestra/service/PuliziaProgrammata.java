/*
 * FILE: PuliziaProgrammata.java
 * A COSA SERVE: ogni notte cancella dal database i refresh token scaduti, che altrimenti si accumulerebbero per sempre (ogni login e
 * ogni rinnovo ne crea uno nuovo). Un token scaduto non serve più a nessuno: sarebbe rifiutato comunque.
 * QUANDO GIRA: secondo l'espressione cron di app.pulizia.cron (predefinita: ogni giorno alle 03:30, formato "secondi minuti ore giorno
 * mese giornoSettimana"). Se l'app è spenta a quell'ora la pulizia salta e riprova la notte dopo.
 * NOTA: se l'app gira in più istanze, ognuna esegue la pulizia (innocuo: è una DELETE che la seconda trova già fatta).
 */
package it.palestra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PuliziaProgrammata {

    private static final Logger LOG = LoggerFactory.getLogger(PuliziaProgrammata.class);

    private final RefreshTokenService refreshTokens;

    public PuliziaProgrammata(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    // Eseguito dallo scheduler di Spring secondo il cron. Scrive nel log quanti token ha cancellato (solo se ce n'erano).
    @Scheduled(cron = "${app.pulizia.cron:0 30 3 * * *}")
    public void puliziaRefreshTokenScaduti() {
        int cancellati = refreshTokens.eliminaScaduti();
        if (cancellati > 0) {
            LOG.info("Pulizia notturna: cancellati {} refresh token scaduti", cancellati);
        }
    }
}
