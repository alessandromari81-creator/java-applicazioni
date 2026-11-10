/*
 * FILE: NavigazionePagine.java
 * A COSA SERVE: prepara per il template HTML tutto ciò che serve a disegnare i controlli di una lista: i numeri di pagina
 * (con i "..." quando sono tante), i link precedente/successiva, le intestazioni cliccabili per ordinare (con la freccia),
 * "Risultati 21–40 di 57" e il link per azzerare la ricerca. Tiene la logica in Java, dove si può provare con i test,
 * invece che dentro il template.
 * Tutti i link nascono da RichiestaPagina.query(), quindi mantengono ricerca, dimensione e ordinamento già scelti.
 */
package it.palestra.pagina;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public class NavigazionePagine {

    // Un elemento della barra dei numeri: un numero di pagina (0 = prima) oppure i puntini di sospensione.
    public record Voce(int numero, boolean corrente, boolean puntini) {
        // Il numero come lo vede la persona: la prima pagina è la "1".
        public int numeroVisibile() { return numero + 1; }
    }

    public static final List<Integer> DIMENSIONI = List.of(10, 20, 50, 100);

    private final String urlBase;
    private final Page<?> pagina;
    private final RichiestaPagina richiesta;

    public NavigazionePagine(String urlBase, Page<?> pagina, RichiestaPagina richiesta) {
        this.urlBase = urlBase;
        this.pagina = pagina;
        this.richiesta = richiesta;
    }

    // true se la pagina chiesta non esiste (oltre l'ultima) pur esistendo dei risultati: succede, per esempio, dopo aver
    // eliminato l'ultimo elemento dell'ultima pagina. Con zero risultati non c'è nessuna "ultima pagina" a cui andare.
    public static boolean oltreLUltima(Page<?> pagina, RichiestaPagina richiesta) {
        return pagina.getTotalPages() > 0 && richiesta.page() >= pagina.getTotalPages();
    }

    // ---- Dati per la riga "Risultati 21–40 di 57"
    public long getTotale() { return pagina.getTotalElements(); }
    public boolean isVuota() { return pagina.getTotalElements() == 0; }
    public int getDa() { return isVuota() || pagina.getContent().isEmpty() ? 0 : pagina.getNumber() * pagina.getSize() + 1; }
    public int getA() { return pagina.getContent().isEmpty() ? 0 : pagina.getNumber() * pagina.getSize() + pagina.getContent().size(); }

    // ---- Dati per la ricerca e la scelta della dimensione
    public String getUrlBase() { return urlBase; }
    public String getQ() { return richiesta.q(); }
    public int getSize() { return richiesta.size(); }
    public String getSortEsplicito() { return richiesta.sortEsplicito(); }
    public List<Integer> getDimensioni() { return DIMENSIONI; }
    public String getUrlAzzeraRicerca() { return urlBase + richiesta.query(0, richiesta.sortEsplicito(), false); }

    // ---- Pagine
    public boolean isHaPagine() { return pagina.getTotalPages() > 1; }
    public boolean isHaPrecedente() { return pagina.getNumber() > 0; }
    public boolean isHaSuccessiva() { return pagina.getNumber() + 1 < pagina.getTotalPages(); }
    public String urlPagina(int numero) { return urlBase + richiesta.query(numero, richiesta.sortEsplicito()); }
    public String getUrlPrecedente() { return urlPagina(pagina.getNumber() - 1); }
    public String getUrlSuccessiva() { return urlPagina(pagina.getNumber() + 1); }

    // I numeri da mostrare: sempre la prima e l'ultima, la pagina corrente con 2 vicine per lato e "…" nei salti di almeno 2 pagine.
    // Esempio con 20 pagine e la corrente 10:  1 … 9 10 11 12 13 … 20
    public List<Voce> getNumeri() {
        int totale = pagina.getTotalPages();
        int corrente = pagina.getNumber();
        TreeSet<Integer> daMostrare = new TreeSet<>();
        if (totale > 0) {
            daMostrare.add(0);
            daMostrare.add(totale - 1);
            for (int i = corrente - 2; i <= corrente + 2; i++) {
                if (i >= 0 && i < totale) daMostrare.add(i);
            }
        }
        List<Voce> voci = new ArrayList<>();
        Integer precedente = null;
        for (int numero : daMostrare) {
            if (precedente != null) {
                int salto = numero - precedente;
                if (salto == 2) {
                    // Manca UNA sola pagina: i puntini non farebbero risparmiare spazio, si mostra la pagina.
                    voci.add(new Voce(precedente + 1, false, false));
                } else if (salto > 2) {
                    voci.add(new Voce(-1, false, true));       // salto di più pagine: "…"
                }
            }
            voci.add(new Voce(numero, numero == corrente, false));
            precedente = numero;
        }
        return voci;
    }

    // ---- Ordinamento dalle intestazioni di colonna
    public boolean ordinatoPer(String campo) { return richiesta.campo().equals(campo); }

    // Il link dell'intestazione: un clic ordina in modo crescente; un secondo clic sulla stessa colonna inverte la direzione.
    // Cambiando ordinamento si riparte dalla prima pagina.
    public String urlOrdina(String campo) {
        Sort.Direction nuova = ordinatoPer(campo) && richiesta.direzione() == Sort.Direction.ASC
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return urlBase + richiesta.query(0, campo + "," + nuova.name().toLowerCase());
    }

    // La freccia accanto al nome della colonna ordinata: ▲ crescente, ▼ decrescente, niente per le altre.
    public String freccia(String campo) {
        if (!ordinatoPer(campo)) return "";
        return richiesta.direzione() == Sort.Direction.ASC ? "▲" : "▼";
    }
}
