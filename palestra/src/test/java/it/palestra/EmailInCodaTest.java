/*
 * FILE: EmailInCodaTest.java
 * A COSA SERVE: test UNITARI (senza Spring e senza database) della logica di una riga della coda delle email: lo stato di partenza, quanto si aspetta dopo
 * ogni fallimento (1, 2, 4... minuti, al massimo un'ora), quando la riga diventa FALLITA e come si tronca il motivo dell'errore.
 * Il giro vero (database, più istanze, server di posta) è provato in CodaEmailTest e CodaEmailConcorrenzaTest.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.model.EmailInCoda;
import it.palestra.model.StatoEmail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailInCodaTest {

    private static final Instant ORA = Instant.parse("2026-11-24T10:00:00Z");

    private EmailInCoda nuova() {
        return new EmailInCoda("mario@example.com", "Oggetto", "<p>html</p>", "testo", ORA);
    }

    @Test
    @DisplayName("Una email nuova è DA_INVIARE, senza tentativi e spedibile subito")
    void statoDiPartenza() {
        EmailInCoda e = nuova();

        assertThat(e.getStato()).isEqualTo(StatoEmail.DA_INVIARE);
        assertThat(e.getTentativi()).isZero();
        assertThat(e.getProssimoTentativo()).isEqualTo(ORA);
        assertThat(e.getCreataIl()).isEqualTo(ORA);
        assertThat(e.getInCorsoDal()).isNull();
        assertThat(e.getUltimoErrore()).isNull();
    }

    @Test
    @DisplayName("L'attesa dopo un fallimento raddoppia (1, 2, 4, 8, 16, 32 minuti) e poi resta a un'ora")
    void attesaCrescente() {
        assertThat(EmailInCoda.attesaDopo(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(EmailInCoda.attesaDopo(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EmailInCoda.attesaDopo(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(EmailInCoda.attesaDopo(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(EmailInCoda.attesaDopo(5)).isEqualTo(Duration.ofMinutes(16));
        assertThat(EmailInCoda.attesaDopo(6)).isEqualTo(Duration.ofMinutes(32));
        assertThat(EmailInCoda.attesaDopo(7)).as("64 minuti sarebbero troppi: tetto a 60").isEqualTo(Duration.ofMinutes(60));
        assertThat(EmailInCoda.attesaDopo(50)).as("nessun overflow con tanti tentativi").isEqualTo(Duration.ofMinutes(60));
        assertThat(EmailInCoda.attesaDopo(0)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Un fallimento conta il tentativo, ricorda il motivo e rimanda la riga a più tardi")
    void unFallimento() {
        EmailInCoda e = nuova();

        e.registraFallimento(ORA, 5, "IllegalStateException: server non raggiungibile");

        assertThat(e.getStato()).isEqualTo(StatoEmail.DA_INVIARE);
        assertThat(e.getTentativi()).isEqualTo(1);
        assertThat(e.getProssimoTentativo()).isEqualTo(ORA.plus(Duration.ofMinutes(1)));
        assertThat(e.getUltimoErrore()).isEqualTo("IllegalStateException: server non raggiungibile");
        assertThat(e.getInCorsoDal()).isNull();
    }

    @Test
    @DisplayName("Raggiunto il massimo dei tentativi la riga diventa FALLITA e non ha più un prossimo tentativo")
    void raggiuntoIlMassimo() {
        EmailInCoda e = nuova();
        Instant ora = ORA;
        for (int i = 1; i <= 4; i++) {
            e.registraFallimento(ora, 5, "errore");
            assertThat(e.getStato()).as("al fallimento %d ci sono ancora tentativi", i).isEqualTo(StatoEmail.DA_INVIARE);
            ora = e.getProssimoTentativo();
        }
        Instant ultimoProssimo = e.getProssimoTentativo();

        e.registraFallimento(ora, 5, "errore");

        assertThat(e.getStato()).isEqualTo(StatoEmail.FALLITA);
        assertThat(e.getTentativi()).isEqualTo(5);
        assertThat(e.getProssimoTentativo()).as("il prossimo tentativo non viene ricalcolato: non si riprova più").isEqualTo(ultimoProssimo);
    }

    @Test
    @DisplayName("Con massimo 1 il primo fallimento è già definitivo")
    void massimoUno() {
        EmailInCoda e = nuova();

        e.registraFallimento(ORA, 1, "errore");

        assertThat(e.getStato()).isEqualTo(StatoEmail.FALLITA);
    }

    @Test
    @DisplayName("Il motivo dell'errore si taglia a 255 caratteri (quanti ne sta nel database) e può mancare")
    void erroreTroncato() {
        EmailInCoda lunga = nuova();
        lunga.registraFallimento(ORA, 5, "x".repeat(1000));
        EmailInCoda senza = nuova();
        senza.registraFallimento(ORA, 5, null);

        assertThat(lunga.getUltimoErrore()).hasSize(255);
        assertThat(senza.getUltimoErrore()).isNull();
    }
}
