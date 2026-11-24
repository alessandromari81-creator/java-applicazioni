/*
 * FILE: DatiUtenteTest.java
 * A COSA SERVE: test UNITARI del record DatiUtente (i dati con cui UtenteService crea o modifica un utente): le liste di ruoli e di
 * corsi non sono mai null e il record non cambia se chi l'ha creato modifica poi la propria lista.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.palestra.service.DatiUtente;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatiUtenteTest {

    @Test
    @DisplayName("Liste assenti (null) diventano vuote")
    void listeAssentiDiventanoVuote() {
        DatiUtente dati = new DatiUtente("Mario", "Rossi", "mario@example.com", null, null, null, null, null, null);

        assertThat(dati.ruoliIds()).isEmpty();
        assertThat(dati.corsiIds()).isEmpty();
    }

    @Test
    @DisplayName("Il record tiene una copia: modificare la lista di partenza non lo cambia")
    void tieneUnaCopia() {
        List<Long> ruoli = new ArrayList<>(List.of(1L, 2L));
        DatiUtente dati = new DatiUtente("Mario", "Rossi", "mario@example.com", null, null, null, null, ruoli, List.of());

        ruoli.add(3L);

        assertThat(dati.ruoliIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Le liste del record sono in sola lettura")
    void listeInSolaLettura() {
        DatiUtente dati = new DatiUtente("Mario", "Rossi", "mario@example.com", null, null, null, null, List.of(1L), List.of());

        assertThatThrownBy(() -> dati.ruoliIds().add(2L)).isInstanceOf(UnsupportedOperationException.class);
    }
}
