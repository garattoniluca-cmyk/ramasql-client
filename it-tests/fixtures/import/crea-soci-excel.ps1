<#
  it-tests/fixtures/import/crea-soci-excel.ps1 - produce soci-excel.csv facendolo salvare a Excel (T9.6).

  Legge soci.csv (UTF-8, scritto da ImportFixtures), riempie un foglio di Excel cella per cella - le date come date
  vere, una colonna "quota" con i decimali come numeri - e lo salva con "CSV (delimitato dal separatore di elenco)"
  nelle impostazioni locali (Local = vero): su un Windows in italiano questo vuol dire punto e virgola, virgola
  decimale, date gg/mm/aaaa, codifica Windows-1252. E' il file che uno studente o un docente ottiene davvero da Excel.
  Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File it-tests\fixtures\import\crea-soci-excel.ps1
#>
$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$righe = Get-Content -LiteralPath (Join-Path $dir 'soci.csv') -Encoding UTF8 | Where-Object { $_ -ne '' }
$out = Join-Path $dir 'soci-excel.csv'
if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out }

$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
try {
    $wb = $excel.Workbooks.Add()
    $ws = $wb.Worksheets.Item(1)
    $intest = @('Tessera', 'Cognome', 'Nome', 'Email', 'Nato il', 'Quota')
    for ($c = 0; $c -lt $intest.Count; $c++) { $ws.Cells.Item(1, $c + 1).Value2 = $intest[$c] }
    $r = 2
    foreach ($riga in ($righe | Select-Object -Skip 1)) {
        $v = $riga.Split(';')
        $ws.Cells.Item($r, 1).Value2 = $v[0]
        $ws.Cells.Item($r, 2).Value2 = $v[1]
        $ws.Cells.Item($r, 3).Value2 = $v[2]
        if ($v[3] -ne '') { $ws.Cells.Item($r, 4).Value2 = $v[3] }
        $d = [datetime]::ParseExact($v[4], 'dd/MM/yyyy', [Globalization.CultureInfo]::InvariantCulture)
        # Formula e' in inglese (DATE e il punto decimale), il foglio mostra la data con il formato del sistema
        $ws.Range("E$r").Formula = "=DATE($($d.Year),$($d.Month),$($d.Day))"
        # quota annuale con decimali (25,5 · 12,75 · 30 …): Excel la scrive con la virgola
        $quota = [double](10 + (($r * 7) % 25)) + (($r % 4) * 0.25)
        $ws.Range("F$r").Formula = '=' + $quota.ToString([Globalization.CultureInfo]::InvariantCulture)
        $r++
    }
    # 6 = xlCSV; ultimo argomento Local = $true (separatore di elenco e formati del sistema)
    $m = [Type]::Missing
    $wb.SaveAs($out, 6, $m, $m, $m, $m, 1, $m, $m, $m, $m, $true)
    $wb.Close($false)
} finally {
    $excel.Quit()
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($excel)
}
Write-Host "Scritto $out"
