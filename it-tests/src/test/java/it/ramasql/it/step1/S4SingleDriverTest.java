/*
 * RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
 */
package it.ramasql.it.step1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ramasql.it.ItServers;
import it.ramasql.it.TestCatalog;
import it.ramasql.it.TestResults;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Spike S4 — «Driver unico?»: MariaDB Connector/J usato sia contro MariaDB sia contro MySQL.
 * Ogni test gira sui due server; le differenze ammesse sono solo quelle <b>del server</b>
 * (es. {@code int(10) unsigned} contro {@code int unsigned}), mai del driver.
 * Le evidenze finiscono in {@code test-results/step1/S4-*.txt} (senza credenziali né hash).
 */
@Tag("step1")
@Tag("it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4SingleDriverTest {

    private static final String FIXTURE = "/it/ramasql/it/step1/s4-fixture.sql";

    // ---------------------------------------------------------------- (a) connessione

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void connessioneEVersione(ItServers server) throws SQLException {
        try (Connection con = server.connect(); Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT VERSION(), @@version_comment")) {
            assertTrue(rs.next());
            String version = rs.getString(1);
            String comment = rs.getString(2);
            DatabaseMetaData md = con.getMetaData();
            TestResults.write("step1", "S4-connessione-" + server.name().toLowerCase() + ".txt",
                    "VERSION() = " + version + "\n@@version_comment = " + comment
                            + "\ndriver = " + md.getDriverName() + " " + md.getDriverVersion()
                            + "\nDatabaseMetaData.getDatabaseProductName() = " + md.getDatabaseProductName()
                            + "\nDatabaseMetaData.getDatabaseProductVersion() = " + md.getDatabaseProductVersion()
                            + "\nautocommit = " + con.getAutoCommit() + "\n");
            assertTrue(md.getDriverName().toLowerCase().contains("mariadb"), "il driver in uso deve essere MariaDB Connector/J");
            assertTrue(con.getAutoCommit(), "connessione in autocommit (nessuna gestione delle transazioni in v1)");
            if (server.isMariaDb()) {
                assertTrue(version.contains("MariaDB"), version);
                assertTrue(version.startsWith("11."), version);
            } else {
                assertFalse(version.contains("MariaDB"), version);
                assertTrue(version.startsWith("8."), version);
                assertTrue(comment.toLowerCase().contains("mysql"), comment);
            }
        }
    }

    // ---------------------------------------------------------------- (b) autenticazione

    /**
     * Rileva con quale plugin si autentica l'utente di test. {@code mysql.user} non è leggibile
     * dall'utente di test (verificato qui sotto), ma {@code SHOW CREATE USER CURRENT_USER()} sì.
     * Il test NON afferma che {@code caching_sha2_password} sia stato esercitato: lo scrive
     * nell'evidenza così com'è (vedi rapporto dello spike).
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void autenticazionePluginRilevato(ItServers server) throws SQLException {
        try (Connection con = server.connect(); Statement st = con.createStatement()) {
            SQLException negato = assertThrows(SQLException.class,
                    () -> st.executeQuery("SELECT plugin FROM mysql.user WHERE user = 'ramasql_test'").close());
            assertEquals(1142, negato.getErrorCode(), "l'utente di test non deve poter leggere mysql.user");

            String createUser;
            try (ResultSet rs = st.executeQuery("SHOW CREATE USER CURRENT_USER()")) {
                assertTrue(rs.next());
                createUser = rs.getString(1);
            }
            String plugin = pluginFrom(createUser, server);
            Map<String, String> vars = new LinkedHashMap<>();
            try (ResultSet rs = st.executeQuery("SHOW VARIABLES WHERE Variable_name IN "
                    + "('default_authentication_plugin','authentication_policy','require_secure_transport')")) {
                while (rs.next()) {
                    vars.put(rs.getString(1), rs.getString(2));
                }
            }
            String cipher;
            try (ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                cipher = rs.next() ? rs.getString(2) : "";
            }
            boolean cachingSha2 = "caching_sha2_password".equals(plugin);
            TestResults.write("step1", "S4-autenticazione-" + server.name().toLowerCase() + ".txt",
                    "utente = " + server.user() + "\nplugin dell'utente (da SHOW CREATE USER) = " + plugin
                            + "\nvariabili = " + vars + "\nTLS della sessione JDBC (Ssl_cipher) = "
                            + (cipher.isEmpty() ? "<nessuno: connessione in chiaro>" : cipher)
                            + "\ncaching_sha2_password esercitato da questo test = " + (cachingSha2 ? "SI" : "NO") + "\n"
                            + (server.isMariaDb() ? "" : evidenzaSha2()));
            assertNotNull(plugin);
            assertFalse(plugin.isBlank(), "plugin di autenticazione non rilevato da: " + withoutHash(createUser));
        }
    }

    /**
     * TLS con lo stesso driver sui due server ({@code sslMode=trust}): è il canale su cui MySQL accetta
     * l'autenticazione completa di {@code caching_sha2_password} senza scambio della chiave RSA.
     */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void connessioneCifrataConSslModeTrust(ItServers server) throws SQLException {
        java.util.Properties p = new java.util.Properties();
        p.setProperty("sslMode", "trust");
        try (Connection con = server.connect(null, p); Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
            assertTrue(rs.next());
            assertFalse(rs.getString(2).isBlank(), "con sslMode=trust la sessione deve essere cifrata");
        }
    }

    // ------------------------------------------------ (b-bis) caching_sha2_password (solo MySQL)

    /** Utente MySQL con plugin {@code caching_sha2_password} (creato dal coordinatore); stessa password di {@code ramasql_test}. */
    private static final String SHA2_USER = "ramasql_test_sha2";

    /** Esiti raccolti dai test sha2 di questa esecuzione, per il file di evidenza. */
    private static final Map<String, String> SHA2_ESITI = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** Connessione a MySQL come utente sha2, con le sole proprietà indicate (la password non finisce mai nei messaggi). */
    private static Connection connectSha2(Map<String, String> extra) throws SQLException {
        String url = System.getenv("RAMASQL_IT_MYSQL_URL");
        String password = System.getenv("RAMASQL_IT_MYSQL_PASSWORD");
        if (url == null || password == null) {
            throw new AssertionError("Mancano RAMASQL_IT_MYSQL_URL / _PASSWORD: il test non si salta.");
        }
        java.util.Properties p = new java.util.Properties();
        p.setProperty("connectTimeout", "5000");
        extra.forEach(p::setProperty);
        p.setProperty("user", SHA2_USER);
        p.setProperty("password", password);
        return DriverManager.getConnection(url, p);
    }

    /** Verifica che l'utente effettivo sia quello sha2 e restituisce il suo plugin (da SHOW CREATE USER). */
    private static String sha2Plugin(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT CURRENT_USER()")) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).startsWith(SHA2_USER + "@"), "utente effettivo: " + rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("SHOW CREATE USER CURRENT_USER()")) {
                assertTrue(rs.next());
                return pluginFrom(rs.getString(1), ItServers.MYSQL);
            }
        }
    }

    private static String sslCipher(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
            return rs.next() ? rs.getString(2) : "";
        }
    }

    /** Sezione sha2 del file di evidenza: «esercitato = SÌ» solo se (a), (b) e il plugin sono stati davvero verificati. */
    private static String evidenzaSha2() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- caching_sha2_password: utente ").append(SHA2_USER)
                .append(" (solo MySQL), driver MariaDB Connector/J ---\n");
        SHA2_ESITI.forEach((k, v) -> sb.append(k).append(" = ").append(v).append('\n'));
        boolean esercitato = "caching_sha2_password".equals(SHA2_ESITI.get("plugin dell'utente sha2 (da SHOW CREATE USER)"))
                && SHA2_ESITI.containsKey("(a) allowPublicKeyRetrieval=true, senza TLS")
                && SHA2_ESITI.containsKey("(b) sslMode=trust");
        sb.append("caching_sha2_password esercitato = ").append(esercitato ? "SÌ" : "NO").append('\n');
        sb.append("nota sulla cache: il plugin tiene sul server una cache degli utenti già autenticati. La prima\n"
                + "autenticazione dopo l'avvio del server, FLUSH PRIVILEGES o un cambio di password è «completa»\n"
                + "(serve TLS oppure la chiave RSA del server); le successive sono «veloci» (nessun requisito).\n"
                + "L'utente di test non può svuotare la cache: il percorso esercitato si deduce dal tentativo (e);\n"
                + "quando (e) fallisce al primo tentativo, il caso a cache fredda resta documentato anche in\n"
                + "S4-sha2-senza-parametri-cache-fredda.txt (file scritto solo quando il caso si presenta).\n");
        return sb.toString();
    }

    /**
     * (e) Primo per ordine: che cosa succede SENZA {@code allowPublicKeyRetrieval} e SENZA TLS. L'esito dipende
     * dalla cache del server: a cache fredda il driver rifiuta (manca la chiave RSA), a cache calda entra per il
     * percorso veloce. Il test asserisce che l'esito sia UNO dei due noti e poi dimostra la dipendenza dalla
     * cache: dopo un'autenticazione completa riuscita, lo stesso tentativo senza parametri DEVE riuscire.
     */
    @Test
    @Order(1)
    void sha2SenzaParametriDipendeDallaCache() throws SQLException {
        String primo;
        try (Connection con = connectSha2(Map.of("sslMode", "disable"))) {
            assertEquals("caching_sha2_password", sha2Plugin(con));
            assertEquals("", sslCipher(con));
            primo = "RIUSCITO (cache del server calda: percorso «veloce»)";
        } catch (SQLException e) {
            String msg = String.valueOf(e.getMessage());
            assertTrue(msg.contains("RSA public key"),
                    "a cache fredda l'errore atteso del driver riguarda la chiave RSA; ricevuto: ["
                            + e.getErrorCode() + "/" + e.getSQLState() + "] " + msg);
            primo = "FALLITO (cache fredda: serve l'autenticazione «completa») con [" + e.getErrorCode() + "/"
                    + e.getSQLState() + "] " + msg.lines().findFirst().orElse("");
            TestResults.write("step1", "S4-sha2-senza-parametri-cache-fredda.txt",
                    "Osservato il " + LocalDateTime.now().withNano(0) + "\n"
                            + "utente = " + SHA2_USER + ", sslMode=disable, allowPublicKeyRetrieval assente\n"
                            + "esito = " + primo + "\n");
        }
        SHA2_ESITI.put("(e) senza allowPublicKeyRetrieval e senza TLS, primo tentativo", primo);
        try (Connection con = connectSha2(Map.of("sslMode", "disable", "allowPublicKeyRetrieval", "true"))) {
            assertEquals("caching_sha2_password", sha2Plugin(con));
            SHA2_ESITI.put("(e) subito dopo, con allowPublicKeyRetrieval=true senza TLS", primo.startsWith("FALLITO")
                    ? "RIUSCITO: percorso «completo» con scambio della chiave RSA (la cache era fredda)"
                    : "RIUSCITO: percorso «veloce» (la cache era già calda: il percorso completo NON è stato esercitato ora)");
        }
        try (Connection con = connectSha2(Map.of("sslMode", "disable"))) {
            assertEquals("caching_sha2_password", sha2Plugin(con));
            SHA2_ESITI.put(primo.startsWith("FALLITO")
                            ? "(e) senza parametri, DOPO un'autenticazione completa riuscita"
                            : "(e) senza parametri, di nuovo (cache già calda: nessuna autenticazione completa in questa esecuzione)",
                    "RIUSCITO (percorso «veloce» dalla cache)");
        }
    }

    /** (a)+(c) {@code allowPublicKeyRetrieval=true} senza TLS: entra, il plugin è davvero caching_sha2_password, canale in chiaro. */
    @Test
    @Order(2)
    void sha2ConAllowPublicKeyRetrievalSenzaTls() throws SQLException {
        try (Connection con = connectSha2(Map.of("sslMode", "disable", "allowPublicKeyRetrieval", "true"))) {
            String plugin = sha2Plugin(con);
            assertEquals("caching_sha2_password", plugin);
            assertEquals("", sslCipher(con), "senza TLS la sessione deve essere in chiaro");
            SHA2_ESITI.put("plugin dell'utente sha2 (da SHOW CREATE USER)", plugin);
            SHA2_ESITI.put("(a) allowPublicKeyRetrieval=true, senza TLS", "RIUSCITO, Ssl_cipher vuoto");
        }
    }

    /** (b)+(c) {@code sslMode=trust}: entra su canale cifrato, senza bisogno della chiave RSA. */
    @Test
    @Order(3)
    void sha2ConSslModeTrust() throws SQLException {
        try (Connection con = connectSha2(Map.of("sslMode", "trust"))) {
            String plugin = sha2Plugin(con);
            assertEquals("caching_sha2_password", plugin);
            String cipher = sslCipher(con);
            assertFalse(cipher.isBlank(), "con sslMode=trust la sessione deve essere cifrata");
            SHA2_ESITI.put("plugin dell'utente sha2 (da SHOW CREATE USER)", plugin);
            SHA2_ESITI.put("(b) sslMode=trust", "RIUSCITO, Ssl_cipher=" + cipher);
        }
    }

    /** (d) L'utente sha2 crea, usa e distrugge un catalogo {@code ramasql_test_*}; fuori dal prefisso non può. */
    @Test
    @Order(4)
    void sha2CreaUsaDistruggeCatalogoDiTest() throws SQLException {
        String name = TestCatalog.requireTestName(
                "ramasql_test_s4_sha2_" + Long.toString(System.currentTimeMillis(), 36));
        try (Connection con = connectSha2(Map.of("sslMode", "disable", "allowPublicKeyRetrieval", "true"));
                Statement st = con.createStatement()) {
            try {
                st.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
                con.setCatalog(name);
                st.execute("CREATE TABLE t (id INT PRIMARY KEY AUTO_INCREMENT, nome VARCHAR(40) NOT NULL) ENGINE=InnoDB");
                assertEquals(2, st.executeUpdate("INSERT INTO t (nome) VALUES ('però'), ('caffè')"));
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*), MAX(nome) FROM t")) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1));
                    assertEquals("però", rs.getString(2));
                }
            } finally {
                st.execute("DROP DATABASE IF EXISTS `" + name + "`");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + name + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "il catalogo di test deve essere stato distrutto");
            }
            SQLException negato = assertThrows(SQLException.class,
                    () -> st.execute("CREATE DATABASE `fuori_prefisso_" + Long.toString(System.nanoTime(), 36) + "`"));
            assertEquals(1044, negato.getErrorCode(), "fuori da ramasql_test_* l'utente sha2 non deve avere privilegi");
            SHA2_ESITI.put("(d) catalogo ramasql_test_s4_sha2_* creato, usato e distrutto dall'utente sha2",
                    "SÌ (fuori dal prefisso: errore 1044)");
        }
    }

    private static String pluginFrom(String createUser, ItServers server) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("IDENTIFIED (?:WITH|VIA) '?([A-Za-z0-9_]+)'?").matcher(createUser);
        if (m.find()) {
            return m.group(1);
        }
        // MariaDB scrive «IDENTIFIED BY PASSWORD '<hash>'» quando il plugin è quello predefinito.
        if (server.isMariaDb() && createUser.contains("IDENTIFIED BY PASSWORD")) {
            return "mysql_native_password";
        }
        return "";
    }

    private static String withoutHash(String createUser) {
        return createUser.replaceAll("(AS|PASSWORD) '[^']*'", "$1 '<omesso>'").replaceAll("AS 0x[0-9A-Fa-f]+", "AS <omesso>");
    }

    // ---------------------------------------------------------------- (c) metadati

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void metadatiDaInformationSchema(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_is")) {
            cat.runScript(FIXTURE);
            Connection con = cat.connection();

            // COLUMNS
            Map<String, String[]> cols = new LinkedHashMap<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, COLUMN_KEY"
                            + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'libro'"
                            + " ORDER BY ORDINAL_POSITION")) {
                ps.setString(1, cat.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cols.put(rs.getString(1), new String[] {rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getString(6), rs.getString(7)});
                    }
                }
            }
            assertEquals(List.of("id", "autore_id", "titolo", "anno", "prezzo", "pubblicato", "registrato",
                    "disponibile", "flag", "maschera"), new ArrayList<>(cols.keySet()));
            // differenza del SERVER, non del driver: MariaDB «int(10) unsigned», MySQL 8 «int unsigned»
            assertTrue(cols.get("id")[0].matches("int(\\(10\\))? unsigned"), cols.get("id")[0]);
            assertEquals("NO", cols.get("id")[1]);
            assertEquals("auto_increment", cols.get("id")[3]);
            assertEquals("PRI", cols.get("id")[5]);
            assertTrue(cols.get("anno")[0].matches("smallint(\\(5\\))? unsigned"), cols.get("anno")[0]);
            assertEquals("YES", cols.get("anno")[1]);
            assertEquals("decimal(8,2)", cols.get("prezzo")[0]);
            assertEquals("9.90", cols.get("prezzo")[2]);
            assertEquals("tinyint(1)", cols.get("disponibile")[0]);
            assertEquals("1", cols.get("disponibile")[2]);
            assertEquals("Titolo: àèìòù €", cols.get("titolo")[4], "commento con lettere accentate ed euro");
            assertEquals("bit(8)", cols.get("maschera")[0]);

            // DEFAULT di una colonna stringa: MariaDB lo restituisce tra apici ('IT'), MySQL senza (IT) — differenza del server
            try (PreparedStatement ps = con.prepareStatement("SELECT COLUMN_DEFAULT, COLUMN_COMMENT FROM information_schema.COLUMNS"
                    + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'autore' AND COLUMN_NAME = ?")) {
                ps.setString(1, cat.name());
                ps.setString(2, "nazione");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(server.isMariaDb() ? "'IT'" : "IT", rs.getString(1));
                }
                ps.setString(2, "nome");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Nome e cognome", rs.getString(2));
                }
            }

            // STATISTICS
            List<String> idx = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS"
                            + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'libro' ORDER BY INDEX_NAME, SEQ_IN_INDEX")) {
                ps.setString(1, cat.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        idx.add(rs.getString(1) + "|" + rs.getInt(2) + "|" + rs.getInt(3) + "|" + rs.getString(4));
                    }
                }
            }
            idx.sort(null); // l'ORDER BY del server non distingue maiuscole e minuscole: si ordina qui
            assertEquals(List.of("PRIMARY|0|1|id", "ix_libro_anno|1|1|anno",
                    "uq_libro_autore_titolo|0|1|autore_id", "uq_libro_autore_titolo|0|2|titolo"), idx);

            // KEY_COLUMN_USAGE + REFERENTIAL_CONSTRAINTS
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME,"
                            + " k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE"
                            + " FROM information_schema.KEY_COLUMN_USAGE k"
                            + " JOIN information_schema.REFERENTIAL_CONSTRAINTS r"
                            + "   ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME"
                            + "  AND r.TABLE_NAME = k.TABLE_NAME"
                            + " WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = 'libro' AND k.REFERENCED_TABLE_NAME IS NOT NULL")) {
                ps.setString(1, cat.name());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "chiave esterna non trovata");
                    assertEquals("fk_libro_autore", rs.getString(1));
                    assertEquals("autore_id", rs.getString(2));
                    assertEquals(cat.name(), rs.getString(3));
                    assertEquals("autore", rs.getString(4));
                    assertEquals("id", rs.getString(5));
                    assertEquals("CASCADE", rs.getString(6));
                    assertEquals("CASCADE", rs.getString(7));
                    assertFalse(rs.next(), "una sola chiave esterna attesa");
                }
            }

            // TABLES: engine e commento
            try (PreparedStatement ps = con.prepareStatement("SELECT ENGINE, TABLE_COMMENT, TABLE_COLLATION"
                    + " FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'autore'")) {
                ps.setString(1, cat.name());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("InnoDB", rs.getString(1));
                    assertEquals("Autori dei libri", rs.getString(2));
                    assertTrue(rs.getString(3).startsWith("utf8mb4_"), rs.getString(3));
                }
            }

            // dati con emoji: andata e ritorno
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT nome, CHAR_LENGTH(nome), LENGTH(nome) FROM autore WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Italo Calvino 😀", rs.getString(1));
                assertEquals(15, rs.getInt(2));
                assertEquals(18, rs.getInt(3), "l'emoji occupa 4 byte: utf8mb4 vero");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void metadatiDaDatabaseMetaData(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_dbmd")) {
            cat.runScript(FIXTURE);
            DatabaseMetaData md = cat.connection().getMetaData();
            StringBuilder evidence = new StringBuilder();

            Map<String, String> cols = new LinkedHashMap<>();
            try (ResultSet rs = md.getColumns(cat.name(), null, "libro", "%")) {
                while (rs.next()) {
                    String line = rs.getString("TYPE_NAME") + "|size=" + rs.getInt("COLUMN_SIZE") + "|dec="
                            + rs.getInt("DECIMAL_DIGITS") + "|null=" + rs.getString("IS_NULLABLE") + "|ai="
                            + rs.getString("IS_AUTOINCREMENT") + "|def=" + rs.getString("COLUMN_DEF") + "|rem="
                            + rs.getString("REMARKS");
                    cols.put(rs.getString("COLUMN_NAME"), line);
                    evidence.append(rs.getString("COLUMN_NAME")).append(" -> ").append(line).append('\n');
                }
            }
            TestResults.write("step1", "S4-dbmd-colonne-" + server.name().toLowerCase() + ".txt", evidence.toString());
            assertEquals(10, cols.size(), "colonne di libro");
            assertEquals("INT UNSIGNED|size=10|dec=0|null=NO|ai=YES|def=null|rem=", cols.get("id"));
            // differenza del SERVER: per una colonna NULL senza DEFAULT MariaDB espone il testo «NULL», MySQL un null vero
            String noDefault = server.isMariaDb() ? "NULL" : "null";
            assertEquals("SMALLINT UNSIGNED|size=5|dec=0|null=YES|ai=NO|def=" + noDefault + "|rem=", cols.get("anno"));
            assertEquals("DECIMAL|size=8|dec=2|null=NO|ai=NO|def=9.90|rem=", cols.get("prezzo"));
            assertEquals("VARCHAR|size=120|dec=0|null=NO|ai=NO|def=null|rem=Titolo: àèìòù €", cols.get("titolo"));
            assertEquals("DATE|size=10|dec=0|null=YES|ai=NO|def=" + noDefault + "|rem=", cols.get("pubblicato"));
            assertEquals("BOOLEAN|size=3|dec=0|null=NO|ai=NO|def=1|rem=", cols.get("disponibile"), "TINYINT(1) è presentato come BOOLEAN");
            assertEquals("BIT|size=8|dec=0|null=YES|ai=NO|def=" + noDefault + "|rem=", cols.get("maschera"));

            List<String> pk = new ArrayList<>();
            try (ResultSet rs = md.getPrimaryKeys(cat.name(), null, "libro")) {
                while (rs.next()) {
                    pk.add(rs.getString("COLUMN_NAME") + "|" + rs.getShort("KEY_SEQ") + "|" + rs.getString("PK_NAME"));
                }
            }
            assertEquals(List.of("id|1|PRIMARY"), pk);

            List<String> idx = new ArrayList<>();
            try (ResultSet rs = md.getIndexInfo(cat.name(), null, "libro", false, false)) {
                while (rs.next()) {
                    idx.add(rs.getString("INDEX_NAME") + "|" + (rs.getBoolean("NON_UNIQUE") ? 1 : 0) + "|"
                            + rs.getShort("ORDINAL_POSITION") + "|" + rs.getString("COLUMN_NAME"));
                }
            }
            idx.sort(null);
            assertEquals(List.of("PRIMARY|0|1|id", "ix_libro_anno|1|1|anno",
                    "uq_libro_autore_titolo|0|1|autore_id", "uq_libro_autore_titolo|0|2|titolo"), idx);

            try (ResultSet rs = md.getImportedKeys(cat.name(), null, "libro")) {
                assertTrue(rs.next(), "getImportedKeys non trova la chiave esterna");
                assertEquals("fk_libro_autore", rs.getString("FK_NAME"));
                assertEquals("autore", rs.getString("PKTABLE_NAME"));
                assertEquals("id", rs.getString("PKCOLUMN_NAME"));
                assertEquals("libro", rs.getString("FKTABLE_NAME"));
                assertEquals("autore_id", rs.getString("FKCOLUMN_NAME"));
                assertEquals(DatabaseMetaData.importedKeyCascade, rs.getShort("DELETE_RULE"));
                assertEquals(DatabaseMetaData.importedKeyCascade, rs.getShort("UPDATE_RULE"));
                assertFalse(rs.next());
            }
            try (ResultSet rs = md.getExportedKeys(cat.name(), null, "autore")) {
                assertTrue(rs.next(), "getExportedKeys non trova la chiave esterna");
                assertEquals("libro", rs.getString("FKTABLE_NAME"));
            }

            // la chiave esterna CASCADE funziona davvero
            cat.execute("DELETE FROM autore WHERE id = 1");
            try (Statement st = cat.connection().createStatement();
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM libro")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "ON DELETE CASCADE deve aver tolto il libro dell'autore 1");
            }
        }
    }

    // ---------------------------------------------------------------- (d) KILL QUERY

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void killQueryInterrompeEntroDueSecondi(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "s4_kill")) {
            Connection lenta = cat.newConnection();
            Connection controllo = cat.newConnection();
            long id;
            try (Statement st = lenta.createStatement(); ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                assertTrue(rs.next());
                id = rs.getLong(1);
            }
            CompletableFuture<String> esito = CompletableFuture.supplyAsync(() -> runSleep(lenta));
            waitUntilSleeping(controllo, id);
            long t0 = System.nanoTime();
            try (Statement st = controllo.createStatement()) {
                st.execute("KILL QUERY " + id);
            }
            String come = esito.get(10, TimeUnit.SECONDS);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            TestResults.write("step1", "S4-killquery-" + server.name().toLowerCase() + ".txt",
                    "KILL QUERY " + "<id>" + " -> SLEEP(30) terminato in " + ms + " ms; esito: " + come + "\n");
            assertTrue(ms < 2000, "interruzione in " + ms + " ms (attesi < 2000)");
            assertInterrupted(come);
            assertStillUsable(lenta, id);
        }
    }

    // ---------------------------------------------------------------- (e) Statement.cancel()

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void statementCancelInterrompeEntroDueSecondi(ItServers server) throws Exception {
        try (TestCatalog cat = TestCatalog.create(server, "s4_cancel")) {
            Connection lenta = cat.newConnection();
            long id;
            try (Statement st = lenta.createStatement(); ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
                assertTrue(rs.next());
                id = rs.getLong(1);
            }
            try (Statement st = lenta.createStatement()) {
                CompletableFuture<String> esito = CompletableFuture.supplyAsync(() -> runSleep(st));
                waitUntilSleeping(cat.connection(), id);
                long t0 = System.nanoTime();
                st.cancel();
                String come = esito.get(10, TimeUnit.SECONDS);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                TestResults.write("step1", "S4-cancel-" + server.name().toLowerCase() + ".txt",
                        "Statement.cancel() -> SLEEP(30) terminato in " + ms + " ms; esito: " + come + "\n");
                assertTrue(ms < 2000, "interruzione in " + ms + " ms (attesi < 2000)");
                assertInterrupted(come);
            }
            assertStillUsable(lenta, id);
        }
    }

    private static String runSleep(Connection con) {
        try (Statement st = con.createStatement()) {
            return runSleep(st);
        } catch (SQLException e) {
            return "errore " + e.getErrorCode() + ": " + e.getMessage();
        }
    }

    /** Esegue SLEEP(30): se interrotto il server risponde 1 (senza errore) oppure dà l'errore 1317/70100. */
    private static String runSleep(Statement st) {
        try (ResultSet rs = st.executeQuery("SELECT SLEEP(30)")) {
            rs.next();
            return "valore " + rs.getInt(1);
        } catch (SQLException e) {
            return "errore " + e.getErrorCode() + " [" + e.getSQLState() + "]: " + e.getMessage();
        }
    }

    private static void assertInterrupted(String come) {
        assertTrue(come.equals("valore 1") || come.startsWith("errore 1317 ") || come.startsWith("errore 1969 "),
                "SLEEP interrotto deve dare 1 oppure l'errore 1317 (MariaDB: anche 1969); ottenuto: " + come);
    }

    /** Aspetta che il server stia davvero eseguendo lo SLEEP su quella connessione (al massimo 5 s). */
    private static void waitUntilSleeping(Connection controllo, long id) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (PreparedStatement ps = controllo.prepareStatement(
                "SELECT INFO FROM information_schema.PROCESSLIST WHERE ID = ?")) {
            ps.setLong(1, id);
            while (System.nanoTime() < limite) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString(1) != null && rs.getString(1).contains("SLEEP(30)")) {
                        return;
                    }
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Lo SLEEP(30) non risulta in esecuzione dopo 5 s");
    }

    private static void assertStillUsable(Connection con, long expectedId) throws SQLException {
        assertFalse(con.isClosed(), "la connessione interrotta deve restare aperta");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT CONNECTION_ID(), 6 * 7")) {
            assertTrue(rs.next());
            assertEquals(expectedId, rs.getLong(1), "stessa sessione del server: KILL QUERY non chiude la connessione");
            assertEquals(42, rs.getInt(2));
        }
    }

    // ---------------------------------------------------------------- (f) getGeneratedKeys

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void chiaviGenerateDopoInsert(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_keys")) {
            cat.runScript(FIXTURE);
            Connection con = cat.connection();
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO autore (nome) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Umberto Eco");
                assertEquals(1, ps.executeUpdate());
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    assertEquals(3L, rs.getLong(1));
                    assertFalse(rs.next());
                }
            }
            // inserimento di più righe con una sola istruzione: il driver 3.x restituisce SOLO la prima chiave
            // (LAST_INSERT_ID del server), su entrambi i server: le altre si ricavano da prima chiave + numero di righe
            try (Statement st = con.createStatement()) {
                assertEquals(3, st.executeUpdate("INSERT INTO autore (nome) VALUES ('A'), ('B'), ('C')",
                        Statement.RETURN_GENERATED_KEYS));
                List<Long> keys = new ArrayList<>();
                try (ResultSet rs = st.getGeneratedKeys()) {
                    while (rs.next()) {
                        keys.add(rs.getLong(1));
                    }
                }
                assertEquals(List.of(4L), keys);
            }
            // il DEFAULT della colonna è stato applicato
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT nazione FROM autore WHERE id = 3")) {
                assertTrue(rs.next());
                assertEquals("IT", rs.getString(1));
            }
        }
    }

    // ---------------------------------------------------------------- (g) tipi

    @ParameterizedTest
    @EnumSource(ItServers.class)
    void tipiJavaDeiValoriLetti(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_tipi")) {
            cat.runScript(FIXTURE);
            StringBuilder evidence = new StringBuilder();
            try (Statement st = cat.connection().createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT id, anno, prezzo, pubblicato, registrato, disponibile, flag, maschera, titolo"
                            + " FROM libro ORDER BY id")) {
                assertTrue(rs.next());
                for (int i = 1; i <= 9; i++) {
                    Object o = rs.getObject(i);
                    evidence.append(rs.getMetaData().getColumnLabel(i)).append(" : ")
                            .append(rs.getMetaData().getColumnTypeName(i)).append(" -> ")
                            .append(o == null ? "null" : o.getClass().getName()).append('\n');
                }
                TestResults.write("step1", "S4-tipi-" + server.name().toLowerCase() + ".txt", evidence.toString());

                // INT UNSIGNED -> Long (non entra in un Integer), SMALLINT UNSIGNED -> Integer
                assertEquals(Long.valueOf(1), rs.getObject("id"));
                assertEquals(Integer.valueOf(1957), rs.getObject("anno"));
                assertEquals(new BigDecimal("12.50"), rs.getObject("prezzo"));
                assertEquals(new BigDecimal("12.50"), rs.getBigDecimal("prezzo"));
                assertEquals(java.sql.Date.valueOf("1957-06-01"), rs.getObject("pubblicato"));
                assertEquals(LocalDate.of(1957, 6, 1), rs.getObject("pubblicato", LocalDate.class));
                assertEquals(java.sql.Timestamp.valueOf("2026-09-21 10:30:45"), rs.getObject("registrato"));
                assertEquals(LocalDateTime.of(2026, 9, 21, 10, 30, 45), rs.getObject("registrato", LocalDateTime.class));
                // TINYINT(1) e BIT(1): il driver li presenta come Boolean, ma il numero resta leggibile
                assertEquals(Boolean.TRUE, rs.getObject("disponibile"));
                assertEquals(1, rs.getInt("disponibile"));
                assertEquals(Boolean.TRUE, rs.getObject("flag"));
                assertArrayEquals(new byte[] {(byte) 0b10100101}, (byte[]) rs.getObject("maschera"));
                assertEquals(0b10100101, rs.getInt("maschera"));
                assertEquals("Il barone rampante 🌳", rs.getString("titolo"));

                assertTrue(rs.next());
                assertEquals(new BigDecimal("1234.05"), rs.getBigDecimal("prezzo"));
                assertEquals(Boolean.FALSE, rs.getObject("disponibile"));
                assertEquals(Boolean.FALSE, rs.getObject("flag"));
            }
        }
    }

    /** TINYINT(1) come numero: con {@code tinyInt1isBit=false} il driver restituisce un intero (serve al data-entry). */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void tinyint1ComeNumeroSeRichiesto(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_tiny")) {
            cat.runScript(FIXTURE);
            cat.execute("UPDATE libro SET disponibile = 7 WHERE id = 2");
            java.util.Properties p = new java.util.Properties();
            p.setProperty("tinyInt1isBit", "false");
            try (Statement st = cat.newConnection(p).createStatement();
                    ResultSet rs = st.executeQuery("SELECT disponibile FROM libro WHERE id = 2")) {
                assertTrue(rs.next());
                assertEquals(7, ((Number) rs.getObject(1)).intValue());
            }
        }
    }

    /** Date zero: si inseriscono togliendo NO_ZERO_DATE alla sessione; il driver le legge come null, il testo resta. */
    @ParameterizedTest
    @EnumSource(ItServers.class)
    void dateZero(ItServers server) throws SQLException {
        try (TestCatalog cat = TestCatalog.create(server, "s4_zero")) {
            cat.runScript(FIXTURE);
            cat.execute("SET SESSION sql_mode = ''",
                    "UPDATE libro SET pubblicato = '0000-00-00', registrato = '0000-00-00 00:00:00' WHERE id = 1");
            try (Statement st = cat.connection().createStatement();
                    ResultSet rs = st.executeQuery("SELECT pubblicato, registrato FROM libro WHERE id = 1")) {
                assertTrue(rs.next());
                String comeTesto = rs.getString(1);
                Object comeData = rs.getDate(1);
                Object comeTimestamp = rs.getTimestamp(2);
                TestResults.write("step1", "S4-datezero-" + server.name().toLowerCase() + ".txt",
                        "getString(DATE zero) = " + comeTesto + "\ngetDate(DATE zero) = " + comeData
                                + "\ngetString(DATETIME zero) = " + rs.getString(2)
                                + "\ngetTimestamp(DATETIME zero) = " + comeTimestamp + "\n");
                assertEquals("0000-00-00", comeTesto);
                assertNull(comeData, "la data zero non è rappresentabile in Java: il driver deve dare null, non un'eccezione");
                assertNull(comeTimestamp);
            }
        }
    }
}
