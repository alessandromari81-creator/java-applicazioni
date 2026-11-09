/*
 * FILE: CorsoRepository.java
 * A COSA SERVE: accesso al database per i corsi. Scrivi solo questa interfaccia e Spring genera da solo
 * l'implementazione con findAll(), findById(), save(), deleteById()...
 * PARAGONE LARAVEL: usare Corso::all(), Corso::find(), ->save() di Eloquent, ma qui
 * le query stanno in una classe a parte (pattern "Repository").
 */
package it.palestra.repository;

import it.palestra.model.Corso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<Corso, Long>: "gestisco entità Corso la cui chiave primaria è di tipo Long".
public interface CorsoRepository extends JpaRepository<Corso, Long> {

    // Tutti i corsi ordinati per nome: la query si ricava dal NOME del metodo
    // (findAll + OrderBy + Nome + Asc -> SELECT ... ORDER BY nome ASC), come ->orderBy('nome')->get().
    // Se il nome non corrisponde a un campo di Corso l'app non parte: l'errore si vede subito,
    // mentre con un testo libero come Sort.by("nome") si scoprirebbe solo a runtime (era l'avviso di VS Code).
    // @EntityGraph carica anche l'istruttore nella stessa query (JOIN): la lista lo mostra per ogni corso e,
    // senza, partirebbe una query per ogni corso (problema "N+1"). In Eloquent: Corso::with('istruttore').
    @EntityGraph(attributePaths = "istruttore")
    List<Corso> findAllByOrderByNomeAsc();

    // Un corso per id con l'istruttore già caricato (serve al form di modifica).
    @EntityGraph(attributePaths = "istruttore")
    Optional<Corso> findWithIstruttoreById(Long id);
}
