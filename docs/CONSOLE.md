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
I test d'integrazione (I) arrivano con lo Step 3.
I test d'integrazione leggono le connessioni da variabili d'ambiente (mai da file in git):
`RAMASQL_IT_MARIADB_URL`, `RAMASQL_IT_MARIADB_USER`, `RAMASQL_IT_MARIADB_PASSWORD` e le tre equivalenti `RAMASQL_IT_MYSQL_*`. Usano e distruggono solo cataloghi con prefisso `ramasql_test_`.

## Installer (solo Step 13, a fine progetto)
```
powershell -File packaging\build-installer.ps1    # jlink → jpackage → Inno Setup → ZIP
```
Prodotti in `dist\`: `RamaSQL-Setup-<ver>.exe`, `RamaSQL-<ver>-portable.zip`, archivio dei sorgenti.
