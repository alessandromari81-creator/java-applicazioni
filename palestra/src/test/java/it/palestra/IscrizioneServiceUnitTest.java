/*
 * FILE: IscrizioneServiceUnitTest.java
 * A COSA SERVE: test UNITARI con Mockito di IscrizioneService (iscrivere e disiscrivere un utente a un corso) e della funzione che prepara il testo
 * della ricerca per LIKE. Niente Spring e niente database: repository e UtenteService sono oggetti FINTI. Si controlla soprattutto che il service
 * chieda la regola sui corsi (con l'elenco dei corsi che l'utente avrebbe DOPO l'iscrizione) e che non aggiunga nulla se la regola dice di no.
 * I comportamenti completi, con il database e le pagine, sono provati in CorsiUtenteTest, IscrizioneCorsiTest e IstruttoriTest.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.IscrizioneService;
import it.palestra.service.UtenteService;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IscrizioneServiceUnitTest {

    @Mock private UtenteRepository utenti;
    @Mock private CorsoRepository corsi;
    @Mock private UtenteService utenteService;

    private IscrizioneService service;

    @BeforeEach
    void creaIlService() {
        service = new IscrizioneService(utenti, corsi, utenteService);
    }

    // ------------------------------------------------------------------ ISCRIVERE

    @Test
    @DisplayName("Iscrivere: chiede la regola con i corsi che avrebbe DOPO (quelli di prima più il nuovo) e poi aggiunge il corso")
    void iscrivere() {
        Corso yoga = corso(10L, "Yoga");
        Corso pilates = corso(11L, "Pilates");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        mario.setCorsi(new HashSet<>(Set.of(yoga)));
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(11L)).thenReturn(Optional.of(pilates));
        when(utenteService.erroreSuiCorsi(eq(1L), any(), any())).thenReturn(Optional.empty());

        Corso risultato = service.iscrivi(1L, 11L);

        assertThat(risultato).isSameAs(pilates);
        assertThat(mario.getCorsi()).containsExactlyInAnyOrder(yoga, pilates);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> corsiDopo = ArgumentCaptor.forClass(List.class);
        verify(utenteService).erroreSuiCorsi(eq(1L), any(), corsiDopo.capture());
        assertThat(corsiDopo.getValue()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("Il blocco sulla riga dell'utente si prende PRIMA di leggere le iscrizioni e di consultare la regola: è l'ordine che evita la doppia iscrizione")
    void ilBloccoSiPrendePrima() {
        Corso pilates = corso(11L, "Pilates");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(11L)).thenReturn(Optional.of(pilates));
        when(utenteService.erroreSuiCorsi(eq(1L), any(), any())).thenReturn(Optional.empty());

        service.iscrivi(1L, 11L);

        // Se la lettura (o la regola) venisse PRIMA del blocco, la seconda di due richieste insieme leggerebbe le iscrizioni vecchie e passerebbe il controllo.
        InOrder ordine = inOrder(utenti, utenteService);
        ordine.verify(utenti).bloccaPerId(1L);
        ordine.verify(utenti).findWithRelazioniById(1L);
        ordine.verify(utenteService).erroreSuiCorsi(eq(1L), any(), any());
    }

    @Test
    @DisplayName("Se la regola dice di no il corso NON viene aggiunto e l'errore ha il messaggio della regola")
    void laRegolaDiceDiNo() {
        Corso yoga = corso(10L, "Yoga");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(10L)).thenReturn(Optional.of(yoga));
        when(utenteService.erroreSuiCorsi(eq(1L), any(), any())).thenReturn(Optional.of("Serve un abbonamento attivo"));

        assertThatThrownBy(() -> service.iscrivi(1L, 10L))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessage("Serve un abbonamento attivo");

        assertThat(mario.getCorsi()).isEmpty();
    }

    @Test
    @DisplayName("Già iscritto: errore e la regola non viene nemmeno consultata")
    void giaIscritto() {
        Corso yoga = corso(10L, "Yoga");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        mario.setCorsi(new HashSet<>(Set.of(yoga)));
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(10L)).thenReturn(Optional.of(yoga));

        assertThatThrownBy(() -> service.iscrivi(1L, 10L))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessage("Mario Rossi è già iscritto al corso Yoga");

        verify(utenteService, never()).erroreSuiCorsi(any(), any(), any());
    }

    @Test
    @DisplayName("Utente o corso inesistenti: 404")
    void inesistenti() {
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.iscrivi(1L, 10L)).isInstanceOf(RisorsaNonTrovataException.class);

        Utente mario = utente(2L, "Mario", "Rossi", Ruolo.ISCRITTO);
        when(utenti.bloccaPerId(2L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(2L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.iscrivi(2L, 99L)).isInstanceOf(RisorsaNonTrovataException.class);
    }

    // ------------------------------------------------------------------ DISISCRIVERE

    @Test
    @DisplayName("Disiscrivere toglie il corso e non consulta la regola (togliere si può sempre)")
    void disiscrivere() {
        Corso yoga = corso(10L, "Yoga");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        mario.setCorsi(new HashSet<>(Set.of(yoga)));
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(10L)).thenReturn(Optional.of(yoga));

        Corso risultato = service.disiscrivi(1L, 10L);

        assertThat(risultato).isSameAs(yoga);
        assertThat(mario.getCorsi()).isEmpty();
        verify(utenteService, never()).erroreSuiCorsi(any(), any(), any());
    }

    @Test
    @DisplayName("Disiscrivere da un corso a cui non è iscritto è un errore")
    void disiscrivereDaUnCorsoNonSeguito() {
        Corso yoga = corso(10L, "Yoga");
        Utente mario = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        when(utenti.bloccaPerId(1L)).thenReturn(Optional.of(mario));
        when(utenti.findWithRelazioniById(1L)).thenReturn(Optional.of(mario));
        when(corsi.findWithIstruttoreById(10L)).thenReturn(Optional.of(yoga));

        assertThatThrownBy(() -> service.disiscrivi(1L, 10L))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessage("Mario Rossi non è iscritto al corso Yoga");
    }

    // ------------------------------------------------------------------ PUÒ ISCRIVERSI

    @Test
    @DisplayName("Si può iscrivere solo con un abbonamento attivo oggi, per tutti (anche per chi non è ISCRITTO)")
    void puoIscriversi() {
        Utente iscritto = utente(1L, "Mario", "Rossi", Ruolo.ISCRITTO);
        Utente istruttore = utente(2L, "Gino", "Verdi", Ruolo.ISTRUTTORE);
        when(utenteService.haAbbonamentoAttivo(1L)).thenReturn(false);
        when(utenteService.haAbbonamentoAttivo(2L)).thenReturn(false);

        assertThat(service.puoIscriversi(iscritto)).isFalse();
        assertThat(service.puoIscriversi(istruttore)).isFalse();

        when(utenteService.haAbbonamentoAttivo(1L)).thenReturn(true);
        assertThat(service.puoIscriversi(iscritto)).isTrue();
    }

    // ------------------------------------------------------------------ TESTO DELLA RICERCA

    @Test
    @DisplayName("Il testo della ricerca per LIKE: «%» se vuoto, altrimenti «%testo%» con %, _ e ! protetti dal carattere di escape")
    void modelloDiRicerca() {
        assertThat(IscrizioneService.modelloDiRicerca(null)).isEqualTo("%");
        assertThat(IscrizioneService.modelloDiRicerca("yoga")).isEqualTo("%yoga%");
        assertThat(IscrizioneService.modelloDiRicerca("50%")).isEqualTo("%50!%%");
        assertThat(IscrizioneService.modelloDiRicerca("a_b")).isEqualTo("%a!_b%");
        assertThat(IscrizioneService.modelloDiRicerca("ciao!")).isEqualTo("%ciao!!%");
        assertThat(IscrizioneService.modelloDiRicerca("!%_")).isEqualTo("%!!!%!_%");
    }

    // ------------------------------------------------------------------ SUPPORTO

    // Un corso solo in memoria con l'id indicato (l'id lo assegna il database, quindi qui si scrive a mano: serve a equals()).
    private Corso corso(Long id, String nome) {
        Corso c = new Corso(nome, null, 60, Livello.PRINCIPIANTE);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    // Un utente solo in memoria con un ruolo.
    private Utente utente(Long id, String nome, String cognome, String nomeRuolo) {
        Utente u = new Utente();
        ReflectionTestUtils.setField(u, "id", id);
        u.setNome(nome);
        u.setCognome(cognome);
        u.setEmail(nome.toLowerCase() + "@example.com");
        Ruolo ruolo = new Ruolo(nomeRuolo);
        ReflectionTestUtils.setField(ruolo, "id", id * 100);
        u.setRuoli(new HashSet<>(Set.of(ruolo)));
        return u;
    }
}
