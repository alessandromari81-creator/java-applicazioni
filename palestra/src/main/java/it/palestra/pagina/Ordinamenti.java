/*
 * FILE: Ordinamenti.java
 * A COSA SERVE: elenca per UNA lista (utenti, corsi, abbonamenti...) per quali campi l'utente può ordinare e qual è
 * l'ordinamento di partenza. Il client scrive un NOME (es. "cognome") e qui si traduce nella proprietà vera della
 * tabella; un nome non previsto viene rifiutato.
 * PERCHÉ UNA LISTA DI CAMPI AMMESSI: se si accettasse qualunque testo, chi invia la richiesta potrebbe ordinare per
 * colonne che non deve poter vedere (es. passwordHash) o far fallire la query con un nome inesistente (errore 500).
 * PARAGONE LARAVEL: l'elenco di colonne "sortable" che si controlla a mano prima di fare orderBy($request->sort).
 * Aggiunge sempre lo "spareggio" sull'id: se due righe hanno lo stesso valore (es. due utenti con lo stesso cognome)
 * senza uno spareggio il database può metterle in ordine diverso da una richiesta all'altra, e passando da una pagina
 * alla successiva alcune righe comparirebbero due volte e altre mai.
 */
package it.palestra.pagina;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Sort;

public final class Ordinamenti {

    private final String predefinito;                   // nome del campo usato se il client non sceglie
    private final Sort.Direction direzionePredefinita;
    private final Map<String, List<String>> campi;      // nome mostrato al client -> proprietà JPA da ordinare (in ordine)

    private Ordinamenti(String predefinito, Sort.Direction direzionePredefinita, Map<String, List<String>> campi) {
        this.predefinito = predefinito;
        this.direzionePredefinita = direzionePredefinita;
        this.campi = campi;
    }

    // Costruisce l'elenco: nome del campo predefinito, sua direzione, e la mappa nome -> proprietà JPA.
    public static Ordinamenti di(String predefinito, Sort.Direction direzione, Map<String, List<String>> campi) {
        if (!campi.containsKey(predefinito)) {
            throw new IllegalArgumentException("Il campo predefinito '" + predefinito + "' non è tra quelli ammessi");
        }
        return new Ordinamenti(predefinito, direzione, campi);
    }

    public boolean ammette(String nome) { return campi.containsKey(nome); }
    public String predefinito() { return predefinito; }
    public Sort.Direction direzionePredefinita() { return direzionePredefinita; }

    // I nomi ammessi in ordine alfabetico (per i messaggi d'errore e per mostrare le intestazioni cliccabili).
    public Set<String> nomi() { return new TreeSet<>(campi.keySet()); }

    // Costruisce l'ordinamento per il database: il campo scelto (con la sua direzione) + lo spareggio sull'id.
    public Sort sort(String nome, Sort.Direction direzione) {
        List<Sort.Order> ordini = campi.get(nome).stream().map(proprieta -> new Sort.Order(direzione, proprieta)).toList();
        return Sort.by(ordini).and(Sort.by(direzione, "id"));
    }
}
