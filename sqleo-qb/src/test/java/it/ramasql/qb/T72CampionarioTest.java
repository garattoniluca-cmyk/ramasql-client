/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.qb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import it.ramasql.qb.T72Campionario.Caso;

/**
 * T7.2 (U, docs/ROADMAP.md Step 7): campionario di query «da aula» ({@link T72Campionario}) passato al parser
 * ereditato da SQLeo, SQL → modello → SQL.
 *
 * <p>Per ogni query: {@link QbSql#check} non lancia eccezioni; l'esito (rappresentabile sì/no) è quello atteso; per le
 * rappresentabili l'SQL rigenerato è <b>equivalente</b> all'originale (è ciò che {@code check} verifica per dichiararla
 * rappresentabile: confronto normalizzato, nessun pezzo di testo perso o cambiato) ed è <b>stabile</b>: ripassato a
 * {@code check} è di nuovo rappresentabile e rigenera lo stesso testo. L'equivalenza «sui dati» la prova T7.3
 * ({@code it-tests}, {@code T73CampionarioSuiServerTest}) eseguendo originale e rigenerata sui due server.
 *
 * <p>Evidenza: {@code test-results/step7/T7.2-campionario.md}.
 */
@Tag("step7")
class T72CampionarioTest {

    private static final String INIZIO = "// --- inizio campionario ---";
    private static final String FINE = "// --- fine campionario ---";

    static Stream<Caso> casi() {
        return T72Campionario.CASI.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casi")
    void ogniQueryHaLEsitoAttesoSenzaEccezioni(Caso caso) {
        String originale = caso.sql();
        QbSql.Result esito = assertDoesNotThrow(() -> QbSql.check(caso.sql()));
        assertNotNull(esito, "esito mancante");
        assertEquals(originale, caso.sql(), "il testo originale non deve cambiare");

        assertEquals(caso.rappresentabile(), esito.representable(),
                "esito diverso dall'atteso; rigenerato: " + esito.regenerated() + " - motivo: " + esito.reason());
        if (esito.representable()) {
            assertNotNull(esito.model(), "rappresentabile ma senza modello");
            assertNotNull(esito.regenerated(), "rappresentabile ma senza SQL rigenerato");
            assertEquals(QbSql.canonicalEqualities(QbSql.normalize(originale)),
                    QbSql.canonicalEqualities(QbSql.normalize(esito.regenerated())), "rigenerato non equivalente");
            QbSql.Result secondoGiro = assertDoesNotThrow(() -> QbSql.check(esito.regenerated()));
            assertTrue(secondoGiro.representable(), "il rigenerato, riletto, non è rappresentabile: " + secondoGiro.reason());
            assertEquals(esito.regenerated(), secondoGiro.regenerated(), "il giro non è stabile");
        } else {
            assertNotNull(esito.reason(), "non rappresentabile ma senza motivo");
            assertTrue(!caso.nota().isBlank(), "una query attesa non rappresentabile deve dire perché (nota)");
        }
    }

    @Test
    void campionarioCompletoETabellaDegliEsiti() throws Exception {
        List<Caso> casi = T72Campionario.CASI;
        long attesiSi = casi.stream().filter(Caso::rappresentabile).count();
        long attesiNo = casi.size() - attesiSi;
        Set<Integer> numeri = new HashSet<>();
        for (int i = 0; i < casi.size(); i++) {
            assertEquals(i + 1, casi.get(i).n(), "numerazione non progressiva");
            assertTrue(numeri.add(casi.get(i).n()));
        }

        StringBuilder md = new StringBuilder();
        md.append("# T7.2 — campionario di query «da aula»: SQL → modello → SQL\n\n");
        md.append("Generato da `").append(getClass().getName()).append("` (campionario: `")
                .append(T72Campionario.class.getName()).append("`, schema `biblioteca`).\n");
        md.append("Il campionario è una **decisione dell'agente da rivedere** (l'elenco di esercizi del corso non c'è ancora).\n\n");
        md.append("«rappresentabile» = il parser produce un modello e l'SQL rigenerato equivale all'originale ")
                .append("(confronto normalizzato: spazi, maiuscole, backtick, AS/INNER/OUTER/ASC facoltativi, ")
                .append("operandi di un'uguaglianza semplice in ordine indifferente); «stabile» = il rigenerato, riletto, ")
                .append("rigenera se stesso.\n\n");
        md.append("| # | Costrutto | Atteso | Ottenuto | Stabile | Esito | SQL originale | SQL rigenerato | Avvisi del parser / motivo / nota |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");

        List<String> falliti = new ArrayList<>();
        int ottenutiSi = 0;
        for (Caso caso : casi) {
            QbSql.Result esito;
            try {
                esito = QbSql.check(caso.sql());
            } catch (RuntimeException | Error e) {
                falliti.add(caso + ": eccezione " + e);
                md.append("| ").append(caso.n()).append(" | ").append(Step7Files.cell(caso.costrutto()))
                        .append(" | | ECCEZIONE | | **FALLITO** | `").append(Step7Files.cell(caso.sql()))
                        .append("` | - | ").append(Step7Files.cell(e.toString())).append(" |\n");
                continue;
            }
            String stabile = "-";
            boolean ok = esito.representable() == caso.rappresentabile();
            if (esito.representable()) {
                ottenutiSi++;
                QbSql.Result secondo = QbSql.check(esito.regenerated());
                boolean st = secondo.representable() && esito.regenerated().equals(secondo.regenerated());
                stabile = st ? "sì" : "NO";
                ok &= st;
            }
            if (!ok) {
                falliti.add(caso + " (rigenerato: " + esito.regenerated() + "; motivo: " + esito.reason() + ")");
            }
            String note = String.join("; ", esito.warnings());
            if (esito.reason() != null) {
                note += (note.isEmpty() ? "" : "; ") + esito.reason();
            }
            if (!caso.nota().isBlank()) {
                note += (note.isEmpty() ? "" : "; ") + "nota: " + caso.nota();
            }
            md.append("| ").append(caso.n())
                    .append(" | ").append(Step7Files.cell(caso.costrutto()))
                    .append(" | ").append(caso.rappresentabile() ? "sì" : "no")
                    .append(" | ").append(esito.representable() ? "sì" : "no")
                    .append(" | ").append(stabile)
                    .append(" | ").append(ok ? "ok" : "**FALLITO**")
                    .append(" | `").append(Step7Files.cell(caso.sql())).append('`')
                    .append(" | ").append(esito.regenerated() == null ? "-" : "`" + Step7Files.cell(esito.regenerated()) + "`")
                    .append(" | ").append(Step7Files.cell(note))
                    .append(" |\n");
        }
        md.append("\nQuery: ").append(casi.size()).append(" (attese rappresentabili ").append(attesiSi)
                .append(", attese solo testo ").append(attesiNo).append("); ottenute rappresentabili: ").append(ottenutiSi)
                .append(".\n\n");
        boolean sogliaOk = attesiSi >= 44 && attesiNo >= 8;
        if (falliti.isEmpty() && sogliaOk) {
            md.append("**Esito: SUPERATO** — ogni query ha l'esito atteso; le rappresentabili sono equivalenti e stabili.\n");
        } else {
            md.append("**Esito: FALLITO**\n\n");
            if (!sogliaOk) {
                md.append("- campionario troppo piccolo (servono ≥ 44 rappresentabili e ≥ 8 solo testo)\n");
            }
            for (String f : falliti) {
                md.append("- ").append(Step7Files.cell(f)).append('\n');
            }
        }
        Step7Files.write("T7.2-campionario.md", md.toString());

        assertTrue(attesiSi >= 44, "servono almeno 44 query rappresentabili, sono " + attesiSi);
        assertTrue(attesiNo >= 8, "servono almeno 8 query non rappresentabili, sono " + attesiNo);
        assertTrue(falliti.isEmpty(), "query con esito diverso dall'atteso:\n" + String.join("\n", falliti));
    }

    /**
     * Il campionario vive in due copie (il modulo {@code it-tests} non vede le classi di test di questo modulo): il
     * blocco fra i marcatori dev'essere identico nei due file sorgente, così T7.3 esegue esattamente le query di T7.2.
     */
    @Test
    void ilCampionarioEUgualeInDueModuli() throws Exception {
        Path radice = Step7Files.projectRoot();
        String qui = blocco(radice.resolve("sqleo-qb/src/test/java/it/ramasql/qb/T72Campionario.java"));
        String la = blocco(radice.resolve("it-tests/src/test/java/it/ramasql/it/step7/Campionario.java"));
        assertEquals(qui, la, "il campionario di it-tests (T7.3) diverge da quello di sqleo-qb (T7.2): ricopiare il blocco");
        long casiNelTesto = qui.lines().filter(r -> r.strip().startsWith("si(") || r.strip().startsWith("no(")).count();
        assertEquals(T72Campionario.CASI.size(), casiNelTesto, "il blocco confrontato non contiene tutti i casi");
    }

    private static String blocco(Path file) throws Exception {
        String testo = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int i = testo.indexOf(INIZIO);
        int f = testo.indexOf(FINE);
        assertTrue(i >= 0 && f > i, "marcatori del campionario assenti in " + file);
        return testo.substring(i, f);
    }
}
