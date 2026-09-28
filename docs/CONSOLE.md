# CONSOLE.md — Comandi del progetto

## Avviare il programma
Doppio clic su **`avvia.cmd`** nella cartella del progetto: compila e apre la finestra. Trova da solo il JDK 25 anche se `JAVA_HOME` non è aggiornato.

## Prerequisiti del PC di sviluppo
- JDK 25 Temurin — installato il 2026-09-21 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`, `JAVA_HOME` di sistema impostata)
- Git · GitHub CLI (`gh`)
- Maven **non** serve: il wrapper `mvnw` scarica Maven 3.9.16 alla prima esecuzione
- Inno Setup 6: **solo per lo Step 13** (installer a fine progetto)
- Attenzione: su questo PC `NoDefaultCurrentDirectoryInExePath=1`, quindi negli script si chiama sempre `.\mvnw.cmd` o `"%~dp0mvnw.cmd"`, mai `mvnw.cmd` da solo.

## Database di sviluppo (PC dell'utente)
| Server | Indirizzo | Servizio Windows |
|---|---|---|
| MariaDB 11.5.2 | `localhost:3306` | `MariaDB` (automatico) |
| MySQL 8.0.40 | `localhost:3307` | `MySQL80` (automatico) |

Credenziali: **solo** in `docs/local DBs.txt` (escluso da git). I cataloghi già presenti (`bibliotecasoft`, `ciccio`, `new_schema` su MariaDB; `scuola` su MySQL) sono **cancellabili ma utili come casi reali di prova**: i test automatici ne usano **copie** dentro `ramasql_test_*`, così restano disponibili; i test manuali possono usarli direttamente.

## Build e test (previsti)
```
.\mvnw.cmd verify                 # compila tutto + test di unità (U)
.\mvnw.cmd -q install -DskipTests  # compila senza test (lo fa avvia.cmd)
.\mvnw.cmd -q -pl app exec:java    # apre la finestra (dopo install)
```
**Verifica completa per step** (carica da sola le credenziali di test; ultima riga `VERIFY: PASS` / `VERIFY: FAIL`):
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1
```
Un solo modulo: `.\mvnw.cmd -q -pl core verify` (le dipendenze interne devono essere già in `~/.m2`: prima `install`).
Schermo piccolo emulato (prove di T12.6/T12.13, anche a mano con `avvia.cmd`): la proprietà di sistema `-Dramasql.screen=1024x768` fa credere al programma di avere uno schermo di quella misura.
Una sola classe di test (utile mentre si lavora): `.\mvnw.cmd -pl app test -Dtest=T47* -DfailIfNoTests=false`; prima `.\mvnw.cmd -q -DskipTests -pl app -am install` e, per i test d'integrazione, le variabili `RAMASQL_IT_*` impostate a mano nella sessione (le stesse righe che carica `scripts\verify.ps1`).
I test d'interfaccia contro i server veri stanno in `app/src/test/java/it/ramasql/app/servertest/` (supporti condivisi `DbServer`, `ClientApp`, `Probe`): avviano il **programma vero** senza mostrare finestre e verificano gli esiti con una connessione separata. `T79ResaScalaTest` (Step 7) avvia il programma in una JVM figlia con `-Dflatlaf.uiScale` (100% e 150%). `T84T87VisteSulServerTest` (Step 8) su MariaDB richiede il catalogo dell'utente `bibliotecasoft`, che legge soltanto e copia in un catalogo `ramasql_test_`.
I test d'integrazione (`@Tag("it")`, modulo `it-tests`) girano contro **entrambi** i server e **falliscono** (non saltano) se le variabili mancano. I test con tag `office` (appunti a blocchi, spike S7) avviano **Excel** e **LibreOffice** nascosti e usano gli appunti di sistema, poi ripristinati: durante la verifica non copiare/incollare (`BUG-009`). Le evidenze (schermate, tabelle esiti) finiscono in `test-results/stepN/`.
I test d'integrazione leggono le connessioni da variabili d'ambiente (mai da file in git):
`RAMASQL_IT_MARIADB_URL`, `RAMASQL_IT_MARIADB_USER`, `RAMASQL_IT_MARIADB_PASSWORD` e le tre equivalenti `RAMASQL_IT_MYSQL_*`. Usano e distruggono solo cataloghi con prefisso `ramasql_test_`.

## File di prova dell'importazione (Step 9)
In `it-tests/fixtures/import/`: `soci.csv`, `libri.json`, `prestiti-errori.csv` (li scrive il generatore, `ImportFixturesTest` controlla che siano aggiornati) e `soci-excel.csv`, salvato **da Excel vero** («CSV (delimitato dal separatore di elenco)» con le impostazioni italiane: punto e virgola, Windows-1252, date gg/mm/aaaa, virgola decimale).
```
java it-tests\src\test\java\it\ramasql\it\fixtures\ImportFixtures.java it-tests\fixtures
powershell -NoProfile -ExecutionPolicy Bypass -File it-tests\fixtures\import\crea-soci-excel.ps1
```
Il CSV da un milione di righe di T9.5 lo genera il test in una cartella temporanea (non va in git).

## Dump e ripristino (Step 10)
I test di T10.8 usano i programmi già presenti sul PC: `mariadb-dump.exe` di MariaDB 11.5 (il suo `mysqldump.exe` è lo stesso programma) e il `mysqldump.exe` **di Oracle** che arriva con MySQL Workbench 8.0 (`C:\Program Files\MySQL\MySQL Workbench 8.0 CE\`). La password passa dalla variabile `MYSQL_PWD` del processo figlio, mai sulla riga di comando. Un dump di Navicat messo in `it-tests\fixtures\navicat\*.sql` viene ripristinato anch'esso (procedura in `test-results\step10\T10.8-navicat-procedura.md`). T10.5 lancia il dump e il ripristino di un milione di righe in un processo Java a parte con `-Xmx64m` (`DumpChild`) per misurare la memoria di picco.

## Versione portabile da provare in aula (prima dell'installer)
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\crea-portabile.ps1
```
Compila, raccoglie le librerie e con jpackage crea `dist\RamaSQL-portabile-<data>\RamaSQL\RamaSQL.exe` con Java incluso (circa 120 MB), più `LEGGIMI.txt`, `LICENZA.txt`, `RamaSQL-sorgenti.zip` (i sorgenti dell'ultimo commit, obbligo GPL) e lo ZIP della cartella. Per questo lo script si rifiuta di partire se ci sono modifiche non in un commit (le evidenze in `test-results\` non contano). Icona e `LEGGIMI.txt` stanno in `packaging\`. Non è firmata: al primo avvio Windows può chiedere *Ulteriori informazioni* → *Esegui comunque*.

## Installer (solo Step 13, a fine progetto)
```
powershell -File packaging\build-installer.ps1    # jlink → jpackage → Inno Setup → ZIP
```
Prodotti in `dist\`: `RamaSQL-Setup-<ver>.exe`, `RamaSQL-<ver>-portable.zip`, archivio dei sorgenti.
