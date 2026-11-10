/*
 * FILE: LimiteTentativiLoginTest.java
 * A COSA SERVE: test UNITARI (senza Spring, con un orologio finto che si fa avanzare a mano) della logica del limite ai tentativi di login:
 * soglie per coppia IP+email e per solo IP, durata della finestra, azzeramento dopo un login riuscito, indipendenza tra indirizzi.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.palestra.exception.TroppiTentativiException;
import it.palestra.service.LimiteTentativiLogin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimiteTentativiLoginTest {

    // Orologio finto: parte da un istante fisso e si sposta solo quando il test lo chiede.
    static class OrologioMobile extends Clock {
        private Instant adesso = Instant.parse("2026-11-10T10:00:00Z");
        void avanza(Duration d) { adesso = adesso.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return adesso; }
    }

    private OrologioMobile orologio;
    private LimiteTentativiLogin limite;

    @BeforeEach
    void prepara() {
        orologio = new OrologioMobile();
        limite = new LimiteTentativiLogin(orologio, 5, 20, 15);     // 5 per coppia, 20 per IP, finestra di 15 minuti
    }

    private void fallisci(String ip, String email, int volte) {
        for (int i = 0; i < volte; i++) limite.registraFallimento(ip, email);
    }

    @Test
    @DisplayName("Fino a 4 fallimenti si può ancora provare; al quinto scatta il blocco")
    void sogliaPerCoppia() {
        fallisci("1.1.1.1", "a@x.it", 4);
        assertThatCode(() -> limite.verifica("1.1.1.1", "a@x.it")).doesNotThrowAnyException();

        limite.registraFallimento("1.1.1.1", "a@x.it");

        assertThatThrownBy(() -> limite.verifica("1.1.1.1", "a@x.it")).isInstanceOf(TroppiTentativiException.class);
    }

    @Test
    @DisplayName("I secondi da attendere sono quelli che mancano alla fine della finestra e diminuiscono col tempo")
    void secondiDaAttendere() {
        fallisci("1.1.1.1", "a@x.it", 5);
        assertThat(attesa("1.1.1.1", "a@x.it")).isBetween(899L, 901L);       // 15 minuti = 900 secondi

        orologio.avanza(Duration.ofMinutes(10));

        assertThat(attesa("1.1.1.1", "a@x.it")).isBetween(299L, 301L);       // ne mancano 5
    }

    private long attesa(String ip, String email) {
        try {
            limite.verifica(ip, email);
            return 0;
        } catch (TroppiTentativiException e) {
            return e.getSecondiAttesa();
        }
    }

    @Test
    @DisplayName("Finita la finestra il blocco cade e il contatore riparte da zero")
    void finestraScaduta() {
        fallisci("1.1.1.1", "a@x.it", 5);
        orologio.avanza(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limite.verifica("1.1.1.1", "a@x.it")).doesNotThrowAnyException();
        fallisci("1.1.1.1", "a@x.it", 4);                                      // un nuovo conteggio: 4 non bastano
        assertThatCode(() -> limite.verifica("1.1.1.1", "a@x.it")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un login riuscito azzera il contatore della coppia IP+email")
    void successoAzzera() {
        fallisci("1.1.1.1", "a@x.it", 4);

        limite.registraSuccesso("1.1.1.1", "a@x.it");
        fallisci("1.1.1.1", "a@x.it", 4);                                      // riparte da 0: 4 non bastano

        assertThatCode(() -> limite.verifica("1.1.1.1", "a@x.it")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Il blocco vale per quell'indirizzo e quell'email: un altro indirizzo o un'altra email non sono toccati")
    void bloccoMirato() {
        fallisci("1.1.1.1", "a@x.it", 5);

        assertThatThrownBy(() -> limite.verifica("1.1.1.1", "a@x.it")).isInstanceOf(TroppiTentativiException.class);
        assertThatCode(() -> limite.verifica("2.2.2.2", "a@x.it")).doesNotThrowAnyException();   // stesso utente, altro indirizzo
        assertThatCode(() -> limite.verifica("1.1.1.1", "b@x.it")).doesNotThrowAnyException();   // stesso indirizzo, altro utente
    }

    @Test
    @DisplayName("Email scritta in modi diversi (maiuscole, spazi) conta come la stessa")
    void emailNormalizzata() {
        limite.registraFallimento("1.1.1.1", "A@X.it");
        limite.registraFallimento("1.1.1.1", " a@x.it ");
        limite.registraFallimento("1.1.1.1", "a@X.IT");
        limite.registraFallimento("1.1.1.1", "a@x.it");
        limite.registraFallimento("1.1.1.1", "A@x.it ");

        assertThatThrownBy(() -> limite.verifica("1.1.1.1", "a@x.it")).isInstanceOf(TroppiTentativiException.class);
    }

    @Test
    @DisplayName("Provando 20 email diverse dallo stesso indirizzo scatta il blocco dell'indirizzo, anche per email nuove")
    void sogliaPerIp() {
        for (int i = 0; i < 20; i++) limite.registraFallimento("3.3.3.3", "utente" + i + "@x.it");

        assertThatThrownBy(() -> limite.verifica("3.3.3.3", "mai-visto@x.it")).isInstanceOf(TroppiTentativiException.class);
        assertThatCode(() -> limite.verifica("4.4.4.4", "mai-visto@x.it")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Il successo non azzera il contatore dell'indirizzo: chi alterna email giuste e sbagliate resta frenato")
    void successoNonAzzeraIlContatoreDelIp() {
        for (int i = 0; i < 20; i++) {
            limite.registraFallimento("3.3.3.3", "u" + i + "@x.it");
            limite.registraSuccesso("3.3.3.3", "mio@x.it");
        }

        assertThatThrownBy(() -> limite.verifica("3.3.3.3", "mio@x.it")).isInstanceOf(TroppiTentativiException.class);
    }

    @Test
    @DisplayName("azzera() svuota tutto")
    void azzera() {
        fallisci("1.1.1.1", "a@x.it", 5);

        limite.azzera();

        assertThatCode(() -> limite.verifica("1.1.1.1", "a@x.it")).doesNotThrowAnyException();
    }
}
