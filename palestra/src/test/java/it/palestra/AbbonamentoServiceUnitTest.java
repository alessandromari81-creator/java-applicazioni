/*
 * FILE: AbbonamentoServiceUnitTest.java
 * A COSA SERVE: test UNITARI delle regole di AbbonamentoService (date di inizio, sovrapposizioni, eliminazione), scritti con Mockito.
 * Niente Spring e niente database: i repository sono oggetti FINTI e l'orologio è fermo a una data scelta. Si prova la sola logica
 * del service; che la query delle sovrapposizioni funzioni davvero sul database lo provano AbbonamentoControllerTest e il vincolo
 * EXCLUDE del database.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Abbonamento;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import it.palestra.repository.AbbonamentoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.AbbonamentoService;
import it.palestra.service.DatiAbbonamento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbbonamentoServiceUnitTest {

    // "Oggi" per tutti i test: l'orologio è fermo al 20 novembre 2026, così i risultati non cambiano col passare dei giorni.
    private static final LocalDate OGGI = LocalDate.of(2026, 11, 20);

    @Mock private AbbonamentoRepository abbonamenti;
    @Mock private UtenteRepository utenti;

    private AbbonamentoService service;

    @BeforeEach
    void creaIlService() {
        Clock orologioFermo = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
        service = new AbbonamentoService(abbonamenti, utenti, orologioFermo);
    }

    @Test
    void rifiutaUnaDataNelPassato() {
        DatiAbbonamento form = form(OGGI.minusDays(1));

        Optional<String> errore = service.erroreSullaDataDiInizio(form);

        assertThat(errore).isPresent();
        assertThat(errore.get()).contains("non può essere nel passato");
        verify(abbonamenti, never()).trovaSovrapposti(any(), any(), any());   // si ferma prima: non serve nemmeno guardare il database
    }

    @Test
    void rifiutaUnaSovrapposizioneEIndicaIlPrimoGiornoLibero() {
        // Il nuovo MENSILE da OGGI finisce OGGI + 1 mese - 1 giorno: il repository finto dice che in quel periodo c'è già un abbonamento.
        DatiAbbonamento form = form(OGGI);
        LocalDate fineNuovo = TipoAbbonamento.MENSILE.calcolaFine(OGGI);
        Abbonamento esistente = new Abbonamento(new Utente(), TipoAbbonamento.MENSILE, OGGI.minusDays(10));
        when(abbonamenti.trovaSovrapposti(1L, OGGI, fineNuovo)).thenReturn(List.of(esistente));
        when(abbonamenti.ultimaDataFine(1L)).thenReturn(esistente.getDataFine());

        Optional<String> errore = service.erroreSullaDataDiInizio(form);

        assertThat(errore).isPresent();
        assertThat(errore.get()).contains("Si sovrappone");
        // Il primo giorno libero è il giorno DOPO la fine dell'abbonamento esistente, scritto gg/mm/aaaa come nel messaggio.
        String attesa = esistente.getDataFine().plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(errore.get()).contains("Il primo giorno libero per questo iscritto è il " + attesa);
    }

    @Test
    void accettaUnaDataLiberaDalPassatoESenzaSovrapposizioni() {
        DatiAbbonamento form = form(OGGI.plusDays(5));
        LocalDate fine = TipoAbbonamento.MENSILE.calcolaFine(OGGI.plusDays(5));
        when(abbonamenti.trovaSovrapposti(1L, OGGI.plusDays(5), fine)).thenReturn(List.of());

        assertThat(service.erroreSullaDataDiInizio(form)).isEmpty();
    }

    @Test
    void primoGiornoLiberoSenzaAbbonamentiEOggi() {
        when(abbonamenti.ultimaDataFine(1L)).thenReturn(null);

        assertThat(service.primoGiornoLibero(1L)).isEqualTo(OGGI);
    }

    @Test
    void primoGiornoLiberoDopoUnAbbonamentoInCorsoEIlGiornoDopoLaFine() {
        when(abbonamenti.ultimaDataFine(1L)).thenReturn(OGGI.plusDays(10));

        assertThat(service.primoGiornoLibero(1L)).isEqualTo(OGGI.plusDays(11));
    }

    @Test
    void primoGiornoLiberoDopoUnAbbonamentoScadutoEOggi() {
        when(abbonamenti.ultimaDataFine(1L)).thenReturn(OGGI.minusDays(30));

        assertThat(service.primoGiornoLibero(1L)).isEqualTo(OGGI);
    }

    @Test
    void nonSiEliminaUnAbbonamentoGiaIniziato() {
        Abbonamento attivo = new Abbonamento(new Utente(), TipoAbbonamento.TRIMESTRALE, OGGI.minusDays(5));
        when(abbonamenti.findConUtenteById(7L)).thenReturn(Optional.of(attivo));

        assertThatThrownBy(() -> service.elimina(7L))
                .isInstanceOf(RegolaViolataException.class)
                .hasMessageContaining("non ancora iniziati");
        verify(abbonamenti, never()).delete(any());
    }

    @Test
    void siEliminaUnAbbonamentoNonAncoraIniziato() {
        Abbonamento futuro = new Abbonamento(new Utente(), TipoAbbonamento.TRIMESTRALE, OGGI.plusDays(5));
        when(abbonamenti.findConUtenteById(7L)).thenReturn(Optional.of(futuro));

        service.elimina(7L);

        verify(abbonamenti).delete(futuro);
    }

    // ------------------------------------------------------------------ SUPPORTO

    // I dati per l'utente 1, abbonamento MENSILE, che parte dal giorno indicato.
    private DatiAbbonamento form(LocalDate inizio) {
        return new DatiAbbonamento(1L, TipoAbbonamento.MENSILE, inizio);
    }
}
