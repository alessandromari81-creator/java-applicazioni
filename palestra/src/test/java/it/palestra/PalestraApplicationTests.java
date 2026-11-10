/*
 * FILE: PalestraApplicationTests.java
 * A COSA SERVE: test generato da Spring Initializr. Verifica solo che l'applicazione
 * riesca ad avviarsi (che tutti i bean si colleghino senza errori).
 * Si esegue con `./mvnw test`. (I test veri li scriveremo nella Fase 6.)
 */
package it.palestra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Avvia l'intero contesto Spring per il test (lento ma realistico).
// @ActiveProfiles("test") carica anche application-test.properties: H2 in memoria al posto di PostgreSQL.
@SpringBootTest
@ActiveProfiles("test")
class PalestraApplicationTests {

	// @Test = questo metodo è un test.
	@Test
	void contextLoads() {
		// Vuoto di proposito: se il contesto non parte, il test fallisce prima di arrivare qui.
	}

}
