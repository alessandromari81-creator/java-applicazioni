/*
 * FILE: StatoLista.java
 * A COSA SERVE: "ricorda" in quale stato era una lista HTML (quale pagina, quanti elementi, ordinamento, ricerca) quando
 * l'utente ha cliccato su Nuovo/Modifica/Elimina, così dopo l'operazione si torna ESATTAMENTE alla stessa vista invece che
 * alla prima pagina. Spring lo riempie da solo con i parametri dell'indirizzo (?page=2&size=50&sort=...&q=...) perché è
 * usato come @ModelAttribute nei controller; nei form viaggia come campi nascosti.
 * SICUREZZA: si ricorda solo il VALORE dei parametri (che viene validato prima di usarlo), mai un indirizzo di ritorno
 * scritto da chi invia la richiesta: l'indirizzo lo costruisce sempre il server (niente "open redirect").
 * PARAGONE LARAVEL: ->withQueryString() sul paginatore, o back() con la querystring.
 */
package it.palestra.pagina;

import org.springframework.web.util.UriComponentsBuilder;

public class StatoLista {

    private Integer page;
    private Integer size;
    private String sort;
    private String q;

    // Valida e trasforma i parametri ricordati in una richiesta pronta per il database (errore 400 se non validi).
    public RichiestaPagina richiesta(Ordinamenti ordinamenti) {
        return RichiestaPagina.da(page, size, sort, q, ordinamenti);
    }

    // La querystring per tornare a questa stessa vista ("" se tutto è predefinito). Valida prima: se un parametro è
    // sbagliato lancia l'errore 400, quindi nel redirect non finisce mai niente di non controllato.
    public String query(Ordinamenti ordinamenti) {
        RichiestaPagina r = richiesta(ordinamenti);
        return r.query(r.page(), r.sortEsplicito());
    }

    // Come query(), ma puntando a un'altra pagina (es. l'ultima, quando quella ricordata non esiste più).
    public String queryPagina(Ordinamenti ordinamenti, int pagina) {
        RichiestaPagina r = richiesta(ordinamenti);
        return r.query(pagina, r.sortEsplicito());
    }

    // La querystring dei valori ricordati, SENZA validarli: serve solo a costruire i link nei template (es. il pulsante
    // "Modifica" che porta al form portandosi dietro lo stato della lista). Valori non validi vengono comunque respinti
    // (400) quando si prova ad usarli per davvero, nei metodi richiesta()/query() qui sopra.
    public String getQuerystring() {
        UriComponentsBuilder b = UriComponentsBuilder.newInstance();
        if (page != null && page > 0) b.queryParam("page", page);
        if (size != null) b.queryParam("size", size);
        if (sort != null && !sort.isBlank()) b.queryParam("sort", sort.trim());
        if (q != null && !q.isBlank()) b.queryParam("q", q.trim());
        return b.build().encode().toUriString();
    }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
}
