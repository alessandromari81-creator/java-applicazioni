/*
 * FILE: PuliziaRefreshTokenTest.java
 * A COSA SERVE: verifica la pulizia dei refresh token scaduti (RefreshTokenService.eliminaScaduti, eseguita ogni notte da
 * PuliziaProgrammata): cancella solo i token già scaduti (usati o no) e lascia quelli ancora validi o revocati-ma-non-scaduti
 * (servono a riconoscere il riuso). Prova anche che il compito programmato sia registrato con il cron configurato.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.model.RefreshToken;
import it.palestra.model.Utente;
import it.palestra.repository.RefreshTokenRepository;
import it.palestra.service.PuliziaProgrammata;
import it.palestra.service.RefreshTokenService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

class PuliziaRefreshTokenTest extends BaseWebTest {

    @Autowired RefreshTokenService service;
    @Autowired PuliziaProgrammata pulizia;
    @Autowired RefreshTokenRepository refreshTokens;

    private RefreshToken crea(Utente u, String hash, long scadeTraGiorni, boolean revocato) {
        Instant ora = Instant.now();
        RefreshToken t = new RefreshToken(u, hash, ora.minus(30, ChronoUnit.DAYS), ora.plus(scadeTraGiorni, ChronoUnit.DAYS));
        if (revocato) t.revoca(ora.minus(1, ChronoUnit.DAYS));
        return refreshTokens.save(t);
    }

    @Test
    @DisplayName("Cancella i token scaduti (anche quelli revocati) e lascia quelli ancora validi")
    void cancellaSoloGliScaduti() {
        Utente u = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        crea(u, "scaduto-mai-usato", -1, false);
        crea(u, "scaduto-e-revocato", -2, true);
        RefreshToken valido = crea(u, "valido", 5, false);
        RefreshToken revocatoMaNonScaduto = crea(u, "revocato-non-scaduto", 3, true);
        refreshTokens.flush();

        int cancellati = service.eliminaScaduti();
        ricarica();

        assertThat(cancellati).isEqualTo(2);
        assertThat(refreshTokens.findAll()).extracting(t -> t.getId())
                .containsExactlyInAnyOrder(valido.getId(), revocatoMaNonScaduto.getId());
    }

    @Test
    @DisplayName("Senza token scaduti non cancella niente e non dà errori")
    void nienteDaCancellare() {
        Utente u = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        crea(u, "valido", 5, false);
        refreshTokens.flush();

        assertThat(service.eliminaScaduti()).isZero();
        assertThat(refreshTokens.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Il compito programmato esegue la pulizia")
    void ilCompitoProgrammatoPulisce() {
        Utente u = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        crea(u, "scaduto", -1, false);
        refreshTokens.flush();

        pulizia.puliziaRefreshTokenScaduti();
        ricarica();

        assertThat(refreshTokens.count()).isZero();
    }

    @Test
    @DisplayName("Il metodo è davvero programmato con @Scheduled (cron configurabile, predefinito ogni notte alle 03:30)")
    void ilCompitoEProgrammato() throws Exception {
        Method metodo = PuliziaProgrammata.class.getMethod("puliziaRefreshTokenScaduti");

        Scheduled programmazione = metodo.getAnnotation(Scheduled.class);

        assertThat(programmazione).isNotNull();
        assertThat(programmazione.cron()).isEqualTo("${app.pulizia.cron:0 30 3 * * *}");
    }
}
