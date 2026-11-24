/*
 * FILE: UtenteServiceUnitTest.java
 * A COSA SERVE: test UNITARI della regola "non si toglie né si elimina l'ultimo amministratore" di UtenteService, scritti con Mockito.
 * Non si avvia Spring e non c'è nessun database: le dipendenze del service (repository, encoder...) sono oggetti FINTI (mock) a cui
 * si dice cosa rispondere, e si controlla cosa fa il service con loro. Sono veloci e provano la sola logica del service; che le query
 * facciano davvero quello che dicono lo provano i test di integrazione (UtenteControllerTest, UltimoAdminConcorrenzaTest...).
 *
 * CHE COSA SI CONTROLLA OLTRE AL RISULTATO: l'ORDINE delle chiamate. Il blocco sulla riga del ruolo ADMIN (bloccaPerNome) deve essere
 * preso PRIMA di contare gli admin: è proprio l'ordine che evita la gara tra due richieste insieme (vedi UltimoAdminConcorrenzaTest).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.AbbonamentoRepository;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.DatiUtente;
import it.palestra.service.RefreshTokenService;
import it.palestra.service.UtenteService;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

// @ExtendWith(MockitoExtension.class): crea i @Mock prima di ogni test e segnala i "finti" preparati ma mai usati.
@ExtendWith(MockitoExtension.class)
class UtenteServiceUnitTest {

    @Mock private UtenteRepository utenti;
    @Mock private CorsoRepository corsi;
    @Mock private RuoloRepository ruoli;
    @Mock private AbbonamentoRepository abbonamenti;
    @Mock private PasswordEncoder encoder;
    @Mock private RefreshTokenService refreshTokens;

    private UtenteService service;

    // Il service VERO, costruito con le dipendenze finte (nessun Spring). L'orologio non serve a queste regole: ne basta uno qualsiasi.
    @BeforeEach
    void creaIlService() {
        service = new UtenteService(utenti, corsi, ruoli, abbonamenti, encoder, refreshTokens, Clock.systemUTC());
    }

    @Test
    void modificaRifiutaDiTogliereAdminAllUltimoAdmin() {
        Utente admin = utenteConRuoli(Ruolo.ADMIN);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(admin));
        when(utenti.countByRuoliNome(Ruolo.ADMIN)).thenReturn(1L);          // è l'unico admin

        assertThatThrownBy(() -> service.modifica(1L, formSenzaRuoli()))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessageContaining("ultimo amministratore");
        assertThat(admin.isAdmin()).as("il ruolo non deve essere stato tolto").isTrue();
    }

    @Test
    void modificaTogliAdminSeCeNeSonoAltri() {
        Utente admin = utenteConRuoli(Ruolo.ADMIN);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(admin));
        when(utenti.countByRuoliNome(Ruolo.ADMIN)).thenReturn(2L);          // ce n'è un altro: si può

        service.modifica(1L, formSenzaRuoli());

        assertThat(admin.isAdmin()).as("il ruolo deve essere stato tolto").isFalse();
    }

    @Test
    void ilBloccoSiPrendePrimaDiContareGliAdmin() {
        Utente admin = utenteConRuoli(Ruolo.ADMIN);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(admin));
        when(utenti.countByRuoliNome(Ruolo.ADMIN)).thenReturn(2L);

        service.modifica(1L, formSenzaRuoli());

        // Se l'ordine fosse l'inverso (prima il conteggio, poi il blocco) la gara tornerebbe: due richieste leggerebbero lo stesso numero.
        InOrder ordine = inOrder(ruoli, utenti);
        ordine.verify(ruoli).bloccaPerNome(Ruolo.ADMIN);
        ordine.verify(utenti).countByRuoliNome(Ruolo.ADMIN);
    }

    @Test
    void chiNonEAdminNonPrendeIlBlocco() {
        Utente semplice = utenteConRuoli(Ruolo.ISCRITTO);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(semplice));

        service.modifica(1L, formSenzaRuoli());

        // Modificare un utente qualunque non deve far aspettare nessuno: il blocco serve solo a chi riduce gli admin.
        verify(ruoli, never()).bloccaPerNome(any());
        verify(utenti, never()).countByRuoliNome(any());
    }

    @Test
    void eliminaRifiutaLUltimoAdmin() {
        Utente admin = utenteConRuoli(Ruolo.ADMIN);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(admin));
        when(utenti.countByRuoliNome(Ruolo.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.elimina(1L))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessageContaining("ultimo amministratore");
        verify(utenti, never()).delete(any());                              // e non ha cancellato niente
    }

    @Test
    void eliminaUnAdminSeCeNeSonoAltri() {
        Utente admin = utenteConRuoli(Ruolo.ADMIN);
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(admin));
        when(utenti.countByRuoliNome(Ruolo.ADMIN)).thenReturn(2L);

        service.elimina(1L);

        verify(ruoli).bloccaPerNome(Ruolo.ADMIN);
        verify(utenti).delete(admin);
    }

    // ------------------------------------------------------------------ SUPPORTO

    // Un utente con i ruoli indicati (solo in memoria, mai salvato). Il Ruolo ha solo il nome: basta a isAdmin().
    private Utente utenteConRuoli(String... nomiRuoli) {
        Utente u = new Utente();
        u.setNome("Mario");
        u.setCognome("Rossi");
        u.setEmail("mario@example.com");
        Set<Ruolo> insieme = new HashSet<>();
        for (String nome : nomiRuoli) {
            insieme.add(new Ruolo(nome));
        }
        u.setRuoli(insieme);
        return u;
    }

    // I dati di una modifica senza nessun ruolo e senza corsi. I repository finti rispondono "lista vuota" a findAllById.
    private DatiUtente formSenzaRuoli() {
        return new DatiUtente("Mario", "Rossi", "mario@example.com", null, null, null, null, List.of(), List.of());
    }
}
