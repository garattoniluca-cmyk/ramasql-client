# CONSOLE.md — Comandi del progetto

Il codice non esiste ancora: questo file si riempie dallo Step 0. Qui sotto i comandi **previsti**, da confermare quando esisteranno.

## Prerequisiti del PC di sviluppo (Step 0)
- JDK 25 Temurin (`JAVA_HOME` impostata) — oggi è presente solo un JRE 8
- Inno Setup 6 (per lo Step 1/S3 e lo Step 13)
- Git · GitHub CLI (`gh`)
- Maven **non** serve: si usa il wrapper `mvnw`

## Build e test (previsti)
```
mvnw verify                      # compila tutto + test di unità (U)
mvnw -pl app exec:java           # avvia l'applicazione in sviluppo
mvnw verify -Pit                 # aggiunge i test d'integrazione (I) su MariaDB e MySQL
```
I test d'integrazione leggono le connessioni da variabili d'ambiente (mai da file in git):
`RAMASQL_IT_MARIADB_URL`, `RAMASQL_IT_MARIADB_USER`, `RAMASQL_IT_MARIADB_PASSWORD` e le tre equivalenti `RAMASQL_IT_MYSQL_*`. Usano e distruggono solo cataloghi con prefisso `ramasql_test_`.

## Installer (previsto)
```
powershell -File packaging\build-installer.ps1    # jlink → jpackage → Inno Setup → ZIP
```
Prodotti in `dist\`: `RamaSQL-Setup-<ver>.exe`, `RamaSQL-<ver>-portable.zip`, archivio dei sorgenti.
