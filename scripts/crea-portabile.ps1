<#
  scripts/crea-portabile.ps1 - versione portabile di RamaSQL Client da provare in aula (non e' l'installer dello Step 13).
  Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\crea-portabile.ps1

  1. Compila il programma (mvnw install, senza test: la verifica e' scripts\verify.ps1).
  2. Raccoglie in app\target\portable-lib il jar dell'app e le sue librerie.
  3. jpackage crea dist\RamaSQL-portabile-<data>\RamaSQL\RamaSQL.exe con un runtime Java ridotto incluso,
     piu' LEGGIMI.txt, LICENZA.txt e RamaSQL-sorgenti.zip (i sorgenti dell'ultimo commit, obbligo GPL),
     e lo ZIP della cartella. dist\ e' escluso da git.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$jdk = (Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory -Filter 'jdk-25*' | Select-Object -First 1).FullName
if (-not $jdk) { throw 'JDK 25 non trovato' }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

& cmd /c ".\mvnw.cmd -q -B -ntp install -DskipTests 2>&1"
if ($LASTEXITCODE -ne 0) { throw "compilazione non riuscita ($LASTEXITCODE)" }
$lib = Join-Path $root 'app\target\portable-lib'
if (Test-Path $lib) { Remove-Item -LiteralPath $lib -Recurse -Force }
& cmd /c ".\mvnw.cmd -q -B -ntp -pl app dependency:copy-dependencies -DoutputDirectory=`"$lib`" -DincludeScope=runtime 2>&1"
if ($LASTEXITCODE -ne 0) { throw "copia delle librerie non riuscita ($LASTEXITCODE)" }
$appJar = Get-ChildItem (Join-Path $root 'app\target') -Filter 'ramasql-app-*.jar' | Select-Object -First 1
Copy-Item $appJar.FullName $lib

$dest = Join-Path $root ('dist\RamaSQL-portabile-' + (Get-Date -Format 'yyyy-MM-dd'))
if (Test-Path $dest) { $dest += '-' + (Get-Date -Format 'HHmm') }
# moduli del JDK: jdeps sulle librerie + lingua italiana, codifiche, accessibilita'
$mods = 'java.base,java.desktop,java.management,java.naming,java.prefs,java.security.jgss,java.sql,jdk.net,' +
        'jdk.localedata,jdk.charsets,jdk.unsupported,jdk.zipfs,jdk.accessibility,jdk.crypto.cryptoki'
& "$jdk\bin\jpackage.exe" --type app-image --name RamaSQL --app-version 0.1.0 --vendor 'Luca Garattoni' `
    --copyright 'GPL-3.0-or-later' --description 'RamaSQL Client - client didattico per MariaDB e MySQL' `
    --input $lib --main-jar $appJar.Name --main-class it.ramasql.app.Main `
    --icon (Join-Path $root 'packaging\RamaSQL.ico') --add-modules $mods `
    --jlink-options '--strip-debug --no-header-files --no-man-pages' --dest $dest
if ($LASTEXITCODE -ne 0) { throw "jpackage non riuscito ($LASTEXITCODE)" }
Copy-Item (Join-Path $root 'packaging\LEGGIMI.txt') $dest
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $dest 'LICENZA.txt')
# i sorgenti che corrispondono al programma (GPL-3, par. 6): l'ultimo commit, senza i file ignorati da git
& git archive --format=zip -o (Join-Path $dest 'RamaSQL-sorgenti.zip') HEAD
if ($LASTEXITCODE -ne 0) { throw "archivio dei sorgenti non riuscito ($LASTEXITCODE)" }
Compress-Archive -Path "$dest\*" -DestinationPath "$dest.zip"
"{0:N0} MB, pronta in $dest (e $dest.zip)" -f ((Get-ChildItem $dest -Recurse | Measure-Object Length -Sum).Sum / 1MB)
