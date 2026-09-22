# RamaSQL Client - Copyright (C) 2026 Luca Garattoni - GPL-3.0-or-later, see LICENSE.
#
# Spike S7: verifica REALE degli appunti a blocchi con LibreOffice Calc, senza finestre (--headless),
# pilotato via UNO dal Python incluso in LibreOffice (program\python.exe).
# Usa un profilo utente TEMPORANEO (-env:UserInstallation): cosi' parte un'istanza separata e non si tocca
# un eventuale LibreOffice aperto dall'utente. Alla fine si chiude con terminate(); come rete di sicurezza si
# termina SOLO l'albero del processo soffice avviato qui (taskkill /PID <nostro pid> /T).
#
# File di scambio come per Excel: una riga per riga del blocco, celle separate da "|", ogni cella in Base64 (UTF-8).
#   paste <profilo> <porta> <outfile>                     incolla gli appunti in A1 e scrive le celle lette
#   copy  <profilo> <porta> <infile> <readyfile> <donefile>  scrive il blocco, lo copia, aspetta il test Java
import base64
import os
import subprocess
import sys
import time

import uno
from com.sun.star.beans import PropertyValue

SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"


def prop(name, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p


def enc(s):
    return base64.b64encode(s.encode("utf-8")).decode("ascii")


def dec(s):
    return base64.b64decode(s).decode("utf-8") if s else ""


def main():
    mode, profile, port = sys.argv[1], sys.argv[2], int(sys.argv[3])
    profile_url = uno.systemPathToFileUrl(os.path.abspath(profile))
    proc = subprocess.Popen([
        SOFFICE, "--headless", "--invisible", "--norestore", "--nologo", "--nodefault", "--nofirststartwizard",
        "-env:UserInstallation=" + profile_url,
        "--accept=socket,host=127.0.0.1,port=%d;urp;" % port])
    desktop = None
    code = 0
    try:
        local = uno.getComponentContext()
        resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
        ctx = None
        deadline = time.time() + 90
        while ctx is None:
            try:
                ctx = resolver.resolve("uno:socket,host=127.0.0.1,port=%d;urp;StarOffice.ComponentContext" % port)
            except Exception:
                if time.time() > deadline:
                    raise RuntimeError("LibreOffice non risponde sulla porta %d" % port)
                time.sleep(0.5)
        smgr = ctx.ServiceManager
        desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
        doc = desktop.loadComponentFromURL("private:factory/scalc", "_blank", 0, (prop("Hidden", True),))
        sheet = doc.Sheets.getByIndex(0)
        controller = doc.CurrentController
        frame = controller.Frame
        dispatcher = smgr.createInstanceWithContext("com.sun.star.frame.DispatchHelper", ctx)

        if mode == "paste":
            out = sys.argv[4]
            controller.select(sheet.getCellByPosition(0, 0))
            clip = smgr.createInstanceWithContext("com.sun.star.datatransfer.clipboard.SystemClipboard", ctx)
            contents = clip.getContents()
            flavors = [f.MimeType for f in contents.getTransferDataFlavors()] if contents is not None else None
            print("formati negli appunti visti da LibreOffice: %r" % (flavors,))
            mode_paste = os.environ.get("CALC_PASTE", "import")
            if mode_paste == "dispatch":
                dispatcher.executeDispatch(frame, ".uno:Paste", "", 0, ())
            elif mode_paste == "transferable":
                controller.insertTransferable(contents)
            else:
                # Incollando testo semplice Calc apre la finestra "Importazione testo", che senza interfaccia non
                # puo' comparire (l'incolla non fa nulla). Si legge allora il testo dagli appunti CON LibreOffice e lo
                # si passa allo stesso motore d'importazione, con le opzioni predefinite di quella finestra:
                # separatore tabulazione (9), delimitatore di testo virgolette (34), UTF-8 (76), dalla riga 1.
                text = None
                for f in contents.getTransferDataFlavors():
                    if f.MimeType.startswith("text/plain;charset=utf-16"):
                        text = contents.getTransferData(f)
                if text is None:
                    raise RuntimeError("negli appunti non c'e' testo")
                tsv = out + ".tsv"
                with open(tsv, "w", encoding="utf-8", newline="") as f:
                    f.write(text)
                doc.close(False)
                doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(tsv), "_blank", 0, (
                    prop("Hidden", True), prop("FilterName", "Text - txt - csv (StarCalc)"),
                    prop("FilterOptions", "9,34,76,1,,0,false,false")))
                sheet = doc.Sheets.getByIndex(0)
            cursor = sheet.createCursor()
            cursor.gotoEndOfUsedArea(False)
            last_row = cursor.RangeAddress.EndRow
            last_col = cursor.RangeAddress.EndColumn
            lines = []
            for r in range(last_row + 1):
                lines.append("|".join(enc(sheet.getCellByPosition(c, r).String) for c in range(last_col + 1)))
            with open(out, "w", encoding="utf-8", newline="\n") as f:
                f.write("\n".join(lines) + "\n")
        else:
            infile, ready, done = sys.argv[4], sys.argv[5], sys.argv[6]
            with open(infile, encoding="utf-8") as f:
                rows = [line.rstrip("\r\n").split("|") for line in f if line.strip()]
            for r, row in enumerate(rows):
                for c, cell in enumerate(row):
                    sheet.getCellByPosition(c, r).String = dec(cell)   # .String = testo, nessuna reinterpretazione
            controller.select(sheet.getCellRangeByPosition(0, 0, len(rows[0]) - 1, len(rows) - 1))
            dispatcher.executeDispatch(frame, ".uno:Copy", "", 0, ())
            with open(ready, "w") as f:
                f.write("pronto")
            deadline = time.time() + 60
            while not os.path.exists(done):
                if time.time() > deadline:
                    raise RuntimeError("Il test Java non ha letto gli appunti entro 60 secondi")
                time.sleep(0.1)
        doc.close(False)
    except Exception as e:  # noqa: BLE001 - si riporta qualunque errore al test
        print("ERRORE: %r" % (e,))
        code = 1
    finally:
        try:
            if desktop is not None:
                desktop.terminate()
        except Exception:
            pass
        try:
            proc.wait(timeout=15)
        except Exception:
            subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)
            print("AVVISO: soffice (PID %d) non si e' chiuso da solo: terminato" % proc.pid)
    print("soffice-pid=%d" % proc.pid)
    sys.exit(code)


main()
