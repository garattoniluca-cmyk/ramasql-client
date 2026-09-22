<#
  scripts/verify.ps1 - verifica unica del progetto (ADR-014).
  Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1

  1. Trova il JDK 25 e carica le credenziali di test da "docs/local DBs.txt" (escluso da git)
     nelle variabili RAMASQL_IT_* (solo se non sono gia' impostate).
  2. Esegue ".\mvnw.cmd -B -ntp verify" (compilazione + tutti i test, integrazione compresa).
  3. Legge i report JUnit (target/test-report/open-test-report.xml di ogni modulo) e conta,
     per ogni step, i test SUPERATI con @Tag("stepN"); quelli con anche @Tag("it") sono i test
     d'integrazione contro MariaDB E MySQL. I test saltati non contano.
  4. Controlla in docs/JOURNAL.md che ogni test U/I/M degli step 1-6 elencato in docs/ROADMAP.md
     compaia in una riga di tabella con l'esito "OK" (segno di spunta verde).
  Ultima riga: "VERIFY: PASS" oppure "VERIFY: FAIL".

  SOGLIE CONGELATE il 2026-09-21 (ADR-014): e' vietato abbassarle o cambiare la logica di conteggio.
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$SPUNTA = [string][char]0x2705   # non $OK: in PowerShell $OK e $ok (conteggi, sotto) sono la stessa variabile

# --- soglie congelate: test superati minimi per step (totale / di cui integrazione) ------------
$soglie = [ordered]@{
    step1 = @{ Tot = 8;  It = 4  }   # spike: parser (S2b, S2d), viste reali (S2c), driver (S4)
    step2 = @{ Tot = 8;  It = 2  }   # connessioni, ServerInfo, profili, diagnosi errori
    step3 = @{ Tot = 12; It = 4  }   # metadati, rischio istruzioni, pipeline, architettura
    step4 = @{ Tot = 45; It = 4  }   # separatore istruzioni, DML, appunti a blocchi, griglia
    step5 = @{ Tot = 60; It = 20 }   # TableDiff (>=60 casi) + andata/ritorno sui due server
    step6 = @{ Tot = 45; It = 12 }   # indici, FK (16 combinazioni), controlli, verifica sul server
}

# --- 1. JDK 25 e credenziali di test -------------------------------------------------------------
$jdk = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) { Write-Host 'JDK 25 non trovato'; Write-Host 'VERIFY: FAIL'; exit 1 }
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$env:Path"

$cred = Join-Path $root 'docs\local DBs.txt'
if (-not $env:RAMASQL_IT_MARIADB_PASSWORD -and (Test-Path -LiteralPath $cred)) {
    $pw = ((Get-Content -LiteralPath $cred -Encoding UTF8) -match '^test=')[0] -replace '^test=', ''
    if ($pw) {
        $env:RAMASQL_IT_MARIADB_URL = 'jdbc:mariadb://127.0.0.1:3306/'
        $env:RAMASQL_IT_MARIADB_USER = 'ramasql_test'
        $env:RAMASQL_IT_MARIADB_PASSWORD = $pw
        $env:RAMASQL_IT_MYSQL_URL = 'jdbc:mariadb://127.0.0.1:3307/'
        $env:RAMASQL_IT_MYSQL_USER = 'ramasql_test'
        $env:RAMASQL_IT_MYSQL_PASSWORD = $pw
    }
}
Write-Host ("Credenziali di integrazione: " + $(if ($env:RAMASQL_IT_MARIADB_PASSWORD) { 'caricate' } else { 'ASSENTI (i test it verranno saltati)' }))

# --- 2. build + test ----------------------------------------------------------------------------
$buildOk = $true
if (-not $SkipBuild) {
    Get-ChildItem $root -Directory | ForEach-Object {
        $rep = Join-Path $_.FullName 'target\test-report'
        if (Test-Path $rep) { Remove-Item -LiteralPath $rep -Recurse -Force }
    }
    & cmd /c ".\mvnw.cmd -B -ntp verify 2>&1" | ForEach-Object {
        if ($_ -match 'Tests run:.*(Failures|Errors): [1-9]|\[ERROR\]|BUILD (SUCCESS|FAILURE)') { Write-Host $_ }
    }
    $buildOk = ($LASTEXITCODE -eq 0)
}
Write-Host ("Build Maven: " + $(if ($buildOk) { 'OK' } else { 'FALLITA' }))

# --- 3. conteggio per tag --------------------------------------------------------------------------
$ok = @{}; $okIt = @{}; $falliti = 0; $saltati = 0; $totale = 0
foreach ($file in Get-ChildItem $root -Recurse -Filter 'open-test-report.xml' -ErrorAction SilentlyContinue |
         Where-Object { $_.FullName -match '\\target\\test-report\\' }) {
    [xml]$doc = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $nodi = @{}; $esiti = @{}
    foreach ($n in $doc.DocumentElement.ChildNodes) {
        if ($n.LocalName -eq 'started') {
            $tags = @(); $tipo = ''
            foreach ($m in $n.ChildNodes) {
                if ($m.LocalName -ne 'metadata') { continue }
                foreach ($c in $m.ChildNodes) {
                    if ($c.LocalName -eq 'tags') { $tags += @($c.ChildNodes | ForEach-Object { $_.InnerText }) }
                    if ($c.LocalName -eq 'type') { $tipo = $c.InnerText }
                }
            }
            $nodi[$n.GetAttribute('id')] = @{ Parent = $n.GetAttribute('parentId'); Tags = $tags; Tipo = $tipo }
        } elseif ($n.LocalName -eq 'finished') {
            foreach ($m in $n.ChildNodes) { if ($m.LocalName -eq 'result') { $esiti[$n.GetAttribute('id')] = $m.GetAttribute('status') } }
        }
    }
    foreach ($id in $nodi.Keys) {
        if ($nodi[$id].Tipo -ne 'TEST') { continue }
        $totale++
        $stato = $esiti[$id]
        if ($stato -eq 'FAILED' -or $stato -eq 'ERRORED') { $falliti++; continue }
        if ($stato -ne 'SUCCESSFUL') { $saltati++; continue }
        # tag ereditati da classe e contenitori
        $tutti = @(); $cur = $id
        while ($cur -and $nodi.ContainsKey($cur)) { $tutti += $nodi[$cur].Tags; $cur = $nodi[$cur].Parent }
        foreach ($t in ($tutti | Where-Object { $_ -match '^step\d+$' } | Select-Object -Unique)) {
            $ok[$t] = 1 + [int]$ok[$t]
            if ($tutti -contains 'it') { $okIt[$t] = 1 + [int]$okIt[$t] }
        }
    }
}
Write-Host ("Test eseguiti: {0} - falliti: {1} - saltati: {2}" -f $totale, $falliti, $saltati)

# --- 4. esiti dei test per step nel diario ---------------------------------------------------------
$roadmap = Get-Content -LiteralPath (Join-Path $root 'docs\ROADMAP.md') -Encoding UTF8
$journal = Get-Content -LiteralPath (Join-Path $root 'docs\JOURNAL.md') -Encoding UTF8
$step = 0; $idPerStep = @{}
foreach ($riga in $roadmap) {
    if ($riga -match '^## Step (\d+)\b') { $step = [int]$Matches[1]; continue }
    if ($step -ge 1 -and $step -le 6 -and $riga -match '^\|\s*((T\d+\.\d+b?)|(S\d[a-d]?))\s*\|\s*(U|I|M|M\+U|U\+I)\s*\|') {
        if (-not $idPerStep[$step]) { $idPerStep[$step] = @() }
        $idPerStep[$step] += $Matches[1]
    }
}

# --- esito ----------------------------------------------------------------------------------------
$pass = $buildOk -and ($falliti -eq 0)
Write-Host ''
Write-Host 'Step | superati (it)  | soglia (it) | diario       | esito'
foreach ($k in $soglie.Keys) {
    $n = [int]$ok[$k]; $ni = [int]$okIt[$k]; $s = $soglie[$k]
    $num = [int]($k -replace 'step', '')
    $mancanti = @($idPerStep[$num] | Where-Object {
        $id = [regex]::Escape($_)
        -not ($journal | Where-Object { $_ -match "^\|\s*$id\b" -and $_.Contains($SPUNTA) })
    })
    $stepOk = ($n -ge $s.Tot) -and ($ni -ge $s.It) -and ($mancanti.Count -eq 0)
    if (-not $stepOk) { $pass = $false }
    Write-Host ("{0,-4} | {1,5} ({2,4})  | {3,4} ({4,3})  | {5,-12} | {6}" -f $num, $n, $ni, $s.Tot, $s.It,
        $(if ($mancanti.Count) { "mancano $($mancanti.Count)" } else { 'completo' }), $(if ($stepOk) { 'PASS' } else { 'FAIL' }))
    if ($mancanti.Count -and $mancanti.Count -le 12) { Write-Host ("       senza esito OK nel diario: " + ($mancanti -join ', ')) }
}
Write-Host ''
if ($pass) { Write-Host 'VERIFY: PASS'; exit 0 } else { Write-Host 'VERIFY: FAIL'; exit 1 }
