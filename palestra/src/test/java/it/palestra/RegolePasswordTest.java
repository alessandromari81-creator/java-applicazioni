/*
 * FILE: RegolePasswordTest.java
 * A COSA SERVE: test UNITARI (senza Spring né database) della regola sulla lunghezza della password: da 8 caratteri a 72 BYTE.
 * Il punto delicato è proprio la differenza tra caratteri e byte: una lettera accentata è un solo carattere ma due byte, e bcrypt
 * ignorerebbe tutto ciò che supera i 72 byte.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.service.RegolePassword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegolePasswordTest {

    @Test
    @DisplayName("7 caratteri sono pochi, 8 vanno bene")
    void minimoOttoCaratteri() {
        assertThat(RegolePassword.errore("1234567")).isPresent();
        assertThat(RegolePassword.errore("12345678")).isEmpty();
    }

    @Test
    @DisplayName("72 byte vanno bene, 73 no")
    void massimoSettantaduByte() {
        assertThat(RegolePassword.errore("a".repeat(72))).isEmpty();
        assertThat(RegolePassword.errore("a".repeat(73))).isPresent();
    }

    @Test
    @DisplayName("Contano i byte, non i caratteri: 37 «è» sono 37 caratteri ma 74 byte")
    void contanoIByteNonICaratteri() {
        assertThat(RegolePassword.errore("è".repeat(36))).as("36 lettere = 72 byte").isEmpty();
        assertThat(RegolePassword.errore("è".repeat(37))).as("37 lettere = 74 byte").isPresent();
    }

    @Test
    @DisplayName("Il messaggio d'errore è quello mostrato all'utente")
    void messaggio() {
        assertThat(RegolePassword.errore("corta")).contains("La password deve avere da 8 a 72 caratteri");
    }

    @Test
    @DisplayName("Il limite di bcrypt: null e più di 72 byte non si confrontano nemmeno")
    void limiteDiBcrypt() {
        assertThat(RegolePassword.entraNelLimiteDiBcrypt(null)).isFalse();
        assertThat(RegolePassword.entraNelLimiteDiBcrypt("a".repeat(72))).isTrue();
        assertThat(RegolePassword.entraNelLimiteDiBcrypt("a".repeat(73))).isFalse();
        assertThat(RegolePassword.entraNelLimiteDiBcrypt("è".repeat(37))).isFalse();
    }
}
