/*
 * FILE: AbbonamentoForm.java
 * A COSA SERVE: i dati del form "Nuovo abbonamento" (iscritto, tipo, data di inizio) con le regole semplici di
 * validazione. Data di fine e prezzo NON sono nel form: si calcolano dal tipo. Le regole che dipendono dal
 * database o dalla data di oggi (solo ISCRITTO, non nel passato, niente sovrapposizioni) sono in AbbonamentoService.
 * PARAGONE LARAVEL: una FormRequest.
 */
package it.palestra.web;

import it.palestra.model.TipoAbbonamento;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class AbbonamentoForm {

    // Id dell'iscritto scelto nella tendina.
    @NotNull(message = "Scegli l'iscritto")
    private Long utenteId;

    // Se arriva un valore che non è uno dei tre tipi, Spring segnala l'errore da solo (vedi messages.properties).
    @NotNull(message = "Scegli il tipo di abbonamento")
    private TipoAbbonamento tipo;

    // @DateTimeFormat(iso = DATE): dice a Spring di leggere la data nel formato aaaa-mm-gg, che è quello che
    // invia <input type="date"> (il browser la mostra poi in formato locale, ma invia sempre aaaa-mm-gg).
    @NotNull(message = "La data di inizio è obbligatoria")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataInizio;

    public Long getUtenteId() { return utenteId; }
    public void setUtenteId(Long utenteId) { this.utenteId = utenteId; }
    public TipoAbbonamento getTipo() { return tipo; }
    public void setTipo(TipoAbbonamento tipo) { this.tipo = tipo; }
    public LocalDate getDataInizio() { return dataInizio; }
    public void setDataInizio(LocalDate dataInizio) { this.dataInizio = dataInizio; }
}
