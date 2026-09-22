# RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
#
# Spike S7: verifica REALE degli appunti a blocchi con Microsoft Excel, via automazione COM.
# Excel resta invisibile (Visible = $false, DisplayAlerts = $false): il desktop dell'utente non si tocca.
# Sicurezza verso l'Excel dell'utente (New-Object -ComObject puo' agganciarsi a un Excel gia' aperto):
#  - subito dopo la creazione si controlla che l'istanza sia NOSTRA: nessuna cartella aperta (Workbooks.Count = 0)
#    e processo (PID dalla finestra Hwnd) avviato dopo l'inizio di questo script. Altrimenti si esce con errore
#    senza toccare Visible/DisplayAlerts, senza Quit e senza terminare nulla;
#  - alla fine si chiude SOLO la cartella creata qui (senza salvare); se ne restano altre aperte (l'utente ne ha
#    aperta una nel frattempo) Excel si rende visibile con gli avvisi attivi e NON si chiude;
#  - Stop-Process solo sul NOSTRO PID, e solo se l'istanza era nostra fin dall'inizio.
#
# Formato dei file di scambio (niente JSON: l'app non ha ancora Jackson): una riga per riga del blocco,
# celle separate da «|», ogni cella in Base64 dell'UTF-8 (una cella vuota è una stringa vuota).
#
#   -Mode Paste : incolla gli appunti di sistema in A1 di un foglio nuovo e scrive le celle lette in -OutFile
#   -Mode Copy  : scrive in A1 il blocco letto da -InFile (celle di testo), fa Range.Copy(), crea -ReadyFile,
#                 aspetta -DoneFile (creato dal test Java dopo aver letto gli appunti), poi chiude Excel
param(
    [Parameter(Mandatory = $true)][ValidateSet('Paste', 'Copy')][string]$Mode,
    [string]$OutFile,
    [string]$InFile,
    [string]$ReadyFile,
    [string]$DoneFile,
    [int]$WaitSeconds = 60
)

$ErrorActionPreference = 'Stop'
$startedAt = Get-Date

Add-Type -Namespace RamaSql -Name Win32 -MemberDefinition @'
[DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
'@

function Invoke-WithRetry([scriptblock]$Action) {
    # Excel appena avviato può rispondere «chiamata rifiutata dal destinatario» (RPC_E_CALL_REJECTED): si riprova
    for ($i = 0; $i -lt 20; $i++) {
        try { return (& $Action) }
        catch {
            if ($i -eq 19) { throw }
            Start-Sleep -Milliseconds 250
        }
    }
}

function Encode([object]$v) {
    if ($null -eq $v) { return '' }
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$v))
}

function Decode([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return '' }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s))
}

$excel = $null; $wb = $null; $ws = $null; $used = $null; $range = $null
$excelPid = 0
$exitCode = 0
$nostro = $false   # diventa $true solo dopo aver verificato che l'istanza e' stata avviata da questo script
try {
    $excel = New-Object -ComObject Excel.Application
    $procId = [uint32]0
    [void][RamaSql.Win32]::GetWindowThreadProcessId([IntPtr](Invoke-WithRetry { $excel.Hwnd }), [ref]$procId)
    $excelPid = [int]$procId
    $cartelleAllInizio = [int](Invoke-WithRetry { $excel.Workbooks.Count })
    $proc = if ($excelPid -gt 0) { Get-Process -Id $excelPid -ErrorAction SilentlyContinue } else { $null }
    if ($cartelleAllInizio -ne 0) {
        throw "l'istanza di Excel ottenuta ha gia' $cartelleAllInizio cartelle aperte (e' quella dell'utente): non la tocco"
    }
    if ($null -eq $proc -or $proc.ProcessName -ne 'EXCEL' -or $proc.StartTime -lt $startedAt) {
        throw "il processo Excel (PID $excelPid) non risulta avviato da questo script: non lo tocco"
    }
    $nostro = $true
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    $wb = Invoke-WithRetry { $excel.Workbooks.Add() }
    $ws = $wb.Worksheets.Item(1)

    if ($Mode -eq 'Paste') {
        Invoke-WithRetry { $ws.Range('A1').Select() | Out-Null }
        Invoke-WithRetry { $ws.Paste() }
        $used = $ws.UsedRange
        $rows = $used.Rows.Count
        $cols = $used.Columns.Count
        $firstRow = $used.Row
        $firstCol = $used.Column
        $lines = New-Object System.Collections.Generic.List[string]
        for ($r = 1; $r -le ($firstRow + $rows - 1); $r++) {
            $cells = @()
            for ($c = 1; $c -le ($firstCol + $cols - 1); $c++) {
                # .Text = ciò che l'utente vede; .Value2 = il valore. Si usa Value2 (niente formattazione locale).
                $cells += (Encode $ws.Cells.Item($r, $c).Value2)
            }
            $lines.Add(($cells -join '|'))
        }
        [IO.File]::WriteAllLines($OutFile, $lines, (New-Object Text.UTF8Encoding($false)))
    }
    else {
        $lines = [IO.File]::ReadAllLines($InFile, [Text.Encoding]::UTF8)
        $rows = $lines.Count
        $cols = ($lines[0] -split '\|', -1).Count
        $range = $ws.Range($ws.Cells.Item(1, 1), $ws.Cells.Item($rows, $cols))
        $range.NumberFormat = '@'   # celle di testo: Excel non deve reinterpretare numeri e date
        for ($r = 0; $r -lt $rows; $r++) {
            $cells = $lines[$r] -split '\|', -1
            for ($c = 0; $c -lt $cols; $c++) {
                $ws.Cells.Item($r + 1, $c + 1).Value2 = (Decode $cells[$c])
            }
        }
        Invoke-WithRetry { $range.Copy() | Out-Null }
        [IO.File]::WriteAllText($ReadyFile, 'pronto')
        $deadline = (Get-Date).AddSeconds($WaitSeconds)
        while (-not (Test-Path -LiteralPath $DoneFile)) {
            if ((Get-Date) -gt $deadline) { throw "Il test Java non ha letto gli appunti entro $WaitSeconds secondi" }
            Start-Sleep -Milliseconds 100
        }
    }
}
catch {
    Write-Output ("ERRORE: " + $_.Exception.Message)
    $exitCode = 1
}
finally {
    $altreCartelle = $false
    if ($nostro -and $null -ne $excel) {
        # si chiude SOLO la cartella creata qui, senza salvare
        if ($null -ne $wb) { try { $wb.Close($false) } catch { } }
        try { $altreCartelle = ([int]$excel.Workbooks.Count -gt 0) } catch { $altreCartelle = $true }
        if ($altreCartelle) {
            # qualcuno (l'utente) ha aperto una cartella in questa istanza: la si lascia all'utente, visibile
            try { $excel.DisplayAlerts = $true; $excel.Visible = $true } catch { }
            Write-Output "AVVISO: nell'istanza di Excel (PID $excelPid) restano cartelle aperte: resa visibile, non chiusa"
        }
        else {
            try { $excel.Quit() } catch { }
        }
    }
    if ($null -ne $excel) {
        try { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($excel) } catch { }
    }
    # tutti i riferimenti COM vanno lasciati, altrimenti Excel resta vivo finche' vive questo PowerShell
    $used = $null; $range = $null; $ws = $null; $wb = $null; $excel = $null
    for ($g = 0; $g -lt 2; $g++) {
        [GC]::Collect()
        [GC]::WaitForPendingFinalizers()
    }
    if ($nostro -and -not $altreCartelle -and $excelPid -gt 0) {
        # rete di sicurezza: solo il NOSTRO Excel (stesso PID, nome EXCEL, avviato dopo l'inizio di questo script,
        # senza cartelle aperte all'inizio e alla fine)
        for ($i = 0; $i -lt 150; $i++) {
            if (-not (Get-Process -Id $excelPid -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Milliseconds 100
        }
        $p = Get-Process -Id $excelPid -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq 'EXCEL' -and $p.StartTime -ge $startedAt) {
            Stop-Process -Id $excelPid -Force -ErrorAction SilentlyContinue
            Write-Output "AVVISO: Excel (PID $excelPid) non si e' chiuso da solo: terminato"
        }
    }
}
Write-Output "excel-pid=$excelPid"
exit $exitCode
