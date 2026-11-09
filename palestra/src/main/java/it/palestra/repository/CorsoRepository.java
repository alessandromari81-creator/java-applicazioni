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
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<Corso, Long>: "gestisco entità Corso la cui chiave primaria è di tipo Long".
public interface CorsoRepository extends JpaRepository<Corso, Long> {

    // Tutti i corsi ordinati per nome: la query si ricava dal NOME del metodo
    // (findAll + OrderBy + Nome + Asc -> SELECT ... ORDER BY nome ASC), come ->orderBy('nome')->get().
    // Se il nome non corrisponde a un campo di Corso l'app non parte: l'errore si vede subito,
    // mentre con un testo libero come Sort.by("nome") si scoprirebbe solo a runtime (era l'avviso di VS Code).
    List<Corso> findAllByOrderByNomeAsc();
}
