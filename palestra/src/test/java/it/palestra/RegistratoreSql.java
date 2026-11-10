/*
 * FILE: RegistratoreSql.java
 * A COSA SERVE: strumento dei test. Hibernate gli passa il testo di OGNI istruzione SQL prima di eseguirla (è registrato in
 * application-test.properties con hibernate.session_factory.statement_inspector); qui si conservano solo quelle che contengono un LIKE,
 * così un test può controllare COME è scritta la query di ricerca (vedi RicercaSqlTest). Non modifica le istruzioni.
 */
package it.palestra;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class RegistratoreSql implements StatementInspector {

    private static final long serialVersionUID = 1L;     // StatementInspector è serializzabile: il numero evita un avviso dell'editor

    // Le istruzioni con LIKE viste finora (statica perché Hibernate crea l'oggetto da solo, non lo possiamo iniettare).
    private static final List<String> ISTRUZIONI_CON_LIKE = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        if (sql != null && sql.toLowerCase().contains(" like ")) {
            ISTRUZIONI_CON_LIKE.add(sql);
        }
        return sql;
    }

    public static List<String> istruzioniConLike() {
        return List.copyOf(ISTRUZIONI_CON_LIKE);
    }

    public static void svuota() {
        ISTRUZIONI_CON_LIKE.clear();
    }
}
