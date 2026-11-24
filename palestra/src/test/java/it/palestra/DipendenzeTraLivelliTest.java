/*
 * FILE: DipendenzeTraLivelliTest.java
 * A COSA SERVE: guardia sulla struttura a livelli. Ogni livello può dipendere solo da quelli più interni:
 *     web, api  →  service  →  repository  →  model
 * quindi, per esempio, una classe in service non può importare nulla da web o da api. Il test legge i sorgenti e controlla gli
 * "import": se qualcuno rimette (per comodità) una form delle pagine dentro un service, il test fallisce e dice quale file e quale riga.
 * Non serve Spring né il database: legge solo i file di src/main/java (Maven esegue i test dalla cartella palestra/).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DipendenzeTraLivelliTest {

    private static final Path RADICE = Path.of("src/main/java/it/palestra");

    // Per ogni cartella (livello): i pacchetti che NON può importare.
    private static final Map<String, List<String>> VIETATI = Map.of(
            "api", List.of("it.palestra.web."),
            "service", List.of("it.palestra.web.", "it.palestra.api."),
            "repository", List.of("it.palestra.web.", "it.palestra.api.", "it.palestra.service."),
            "model", List.of("it.palestra.web.", "it.palestra.api.", "it.palestra.service.", "it.palestra.repository."),
            "exception", List.of("it.palestra.web.", "it.palestra.api.", "it.palestra.service.", "it.palestra.repository."));

    @Test
    @DisplayName("Nessun livello importa classi di un livello più esterno")
    void nessunLivelloDipendeDaUnoPiuEsterno() throws IOException {
        List<String> violazioni = new ArrayList<>();
        for (Map.Entry<String, List<String>> regola : VIETATI.entrySet()) {
            Path cartella = RADICE.resolve(regola.getKey());
            List<Path> sorgenti;
            try (Stream<Path> file = Files.walk(cartella)) {
                sorgenti = file.filter(f -> f.toString().endsWith(".java")).toList();
            }
            for (Path sorgente : sorgenti) {
                List<String> righe = Files.readAllLines(sorgente);
                for (int i = 0; i < righe.size(); i++) {
                    String riga = righe.get(i).trim();
                    for (String vietato : regola.getValue()) {
                        if (riga.startsWith("import " + vietato) || riga.startsWith("import static " + vietato)) {
                            violazioni.add(sorgente + ":" + (i + 1) + " importa " + vietato + "*");
                        }
                    }
                }
            }
        }
        assertThat(violazioni).as("import vietati tra livelli").isEmpty();
    }
}
