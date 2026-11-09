/*
 * FILE: CorsoRepository.java
 * A COSA SERVE: accesso al database per i corsi. Scrivi solo questa interfaccia VUOTA e
 * Spring genera da solo l'implementazione con findAll(), findById(), save(), deleteById()...
 * PARAGONE LARAVEL: usare Corso::all(), Corso::find(), ->save() di Eloquent, ma qui
 * le query stanno in una classe a parte (pattern "Repository").
 */
package it.palestra.repository;

import it.palestra.model.Corso;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<Corso, Long>: "gestisco entità Corso la cui chiave primaria è di tipo Long".
public interface CorsoRepository extends JpaRepository<Corso, Long> {
}
