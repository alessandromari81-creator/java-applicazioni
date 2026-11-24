/*
 * FILE: OrdinamentiListe.java
 * A COSA SERVE: le regole di ordinamento di ciascuna lista dell'applicazione, in UN solo posto, usate sia dalle pagine
 * HTML sia dalla API JSON (così le due versioni della stessa lista si comportano allo stesso modo).
 */
package it.palestra.pagina;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;

public final class OrdinamentiListe {

    private OrdinamentiListe() {}   // classe di sole costanti: non si istanzia

    // Utenti: per cognome (poi nome), oppure per nome o email.
    public static final Ordinamenti UTENTI = Ordinamenti.di("cognome", Sort.Direction.ASC, Map.of(
            "cognome", List.of("cognome", "nome"),
            "nome", List.of("nome", "cognome"),
            "email", List.of("email")));

    // Corsi: per nome, livello, durata o id (l'id è utile a chi usa la API: ordine di creazione).
    public static final Ordinamenti CORSI = Ordinamenti.di("nome", Sort.Direction.ASC, Map.of(
            "id", List.of("id"),
            "nome", List.of("nome"),
            "livello", List.of("livello", "nome"),
            "durataMinuti", List.of("durataMinuti", "nome")));

    // Abbonamenti (elenco generale): dal più recente per data di fine. "iscritto" ordina per cognome e nome della persona.
    // Corsi a cui si può iscrivere un utente (pagina «Corsi» dell'utente): per nome (predefinito), livello o durata. L'id come spareggio lo aggiunge Ordinamenti.
    public static final Ordinamenti CORSI_DISPONIBILI = Ordinamenti.di("nome", Sort.Direction.ASC, Map.of(
            "nome", List.of("nome"),
            "livello", List.of("livello", "nome"),
            "durataMinuti", List.of("durataMinuti", "nome")));

    public static final Ordinamenti ABBONAMENTI = Ordinamenti.di("dataFine", Sort.Direction.DESC, Map.of(
            "dataFine", List.of("dataFine"),
            "dataInizio", List.of("dataInizio"),
            "prezzo", List.of("prezzo"),
            "tipo", List.of("tipo", "dataInizio"),
            "iscritto", List.of("utente.cognome", "utente.nome")));

    // Storico di UN utente: dal più recente per data di inizio.
    public static final Ordinamenti ABBONAMENTI_DI_UTENTE = Ordinamenti.di("dataInizio", Sort.Direction.DESC, Map.of(
            "dataInizio", List.of("dataInizio"),
            "dataFine", List.of("dataFine"),
            "prezzo", List.of("prezzo"),
            "tipo", List.of("tipo", "dataInizio")));
}
