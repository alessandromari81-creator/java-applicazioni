/*
 * FILE: PasswordServiceUnitTest.java
 * A COSA SERVE: test UNITARI con Mockito di PasswordService (confronto e cambio della password di un utente esistente). Niente Spring e
 * niente database: repository, encoder e servizio dei refresh token sono oggetti FINTI. Si controlla soprattutto il comportamento che
 * conta per la sicurezza: dopo il cambio, i token vecchi non valgono più, e una password troppo lunga non arriva nemmeno a bcrypt.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Utente;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.PasswordService;
import it.palestra.service.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordServiceUnitTest {

    @Mock private UtenteRepository utenti;
    @Mock private PasswordEncoder encoder;
    @Mock private RefreshTokenService refreshTokens;

    private PasswordService service;

    @BeforeEach
    void creaIlService() {
        service = new PasswordService(utenti, encoder, refreshTokens);
    }

    @Test
    @DisplayName("La password giusta corrisponde (si confrontano gli hash)")
    void passwordGiusta() {
        Utente u = utenteConHash("hash-salvato");
        when(utenti.findByEmailIgnoreCase("mario@example.com")).thenReturn(Optional.of(u));
        when(encoder.matches("password-giusta", "hash-salvato")).thenReturn(true);

        assertThat(service.passwordCorrisponde("mario@example.com", "password-giusta")).isTrue();
    }

    @Test
    @DisplayName("La password sbagliata non corrisponde")
    void passwordSbagliata() {
        Utente u = utenteConHash("hash-salvato");
        when(utenti.findByEmailIgnoreCase("mario@example.com")).thenReturn(Optional.of(u));
        when(encoder.matches("sbagliata", "hash-salvato")).thenReturn(false);

        assertThat(service.passwordCorrisponde("mario@example.com", "sbagliata")).isFalse();
    }

    @Test
    @DisplayName("Un utente senza password (scheda creata dall'admin) non corrisponde mai")
    void utenteSenzaPassword() {
        Utente u = utenteConHash(null);
        when(utenti.findByEmailIgnoreCase("mario@example.com")).thenReturn(Optional.of(u));

        assertThat(service.passwordCorrisponde("mario@example.com", "qualunque-cosa")).isFalse();
    }

    @Test
    @DisplayName("Un'email sconosciuta non corrisponde")
    void emailSconosciuta() {
        when(utenti.findByEmailIgnoreCase("nessuno@example.com")).thenReturn(Optional.empty());

        assertThat(service.passwordCorrisponde("nessuno@example.com", "password-qualsiasi")).isFalse();
    }

    @Test
    @DisplayName("Oltre i 72 byte non si guarda nemmeno il database né bcrypt")
    void passwordTroppoLunga() {
        assertThat(service.passwordCorrisponde("mario@example.com", "a".repeat(73))).isFalse();

        verifyNoInteractions(utenti, encoder);
    }

    @Test
    @DisplayName("Cambiando la password: nuovo hash, access token e refresh token vecchi revocati")
    void cambioPassword() {
        Utente u = utenteConHash("hash-vecchio");
        int versionePrima = u.getVersioneToken();
        when(utenti.findByEmailIgnoreCase("mario@example.com")).thenReturn(Optional.of(u));
        when(encoder.encode("nuova-password")).thenReturn("hash-nuovo");

        service.cambiaPassword("mario@example.com", "nuova-password");

        assertThat(u.getPasswordHash()).isEqualTo("hash-nuovo");
        assertThat(u.getVersioneToken()).as("la versione dei token sale: gli access token vecchi non valgono più").isGreaterThan(versionePrima);
        verify(refreshTokens).revocaTuttiDi(any());
    }

    @Test
    @DisplayName("Cambiare la password di un'email sconosciuta dà 404")
    void cambioPasswordDiUtenteSconosciuto() {
        when(utenti.findByEmailIgnoreCase("nessuno@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiaPassword("nessuno@example.com", "nuova-password"))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    // Un utente solo in memoria, con l'hash indicato (null = nessuna password).
    private Utente utenteConHash(String hash) {
        Utente u = new Utente();
        u.setEmail("mario@example.com");
        u.setPasswordHash(hash);
        return u;
    }
}
