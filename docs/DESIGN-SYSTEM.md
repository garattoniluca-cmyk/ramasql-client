# DESIGN-SYSTEM.md — Sistema visivo di RamaSQL Client

Versione 1.0 — 2026-09-22. Richiesta dell'utente: **«il livello grafico di dettaglio e qualità dei menu deve superare quello di Navicat di slancio; deve sembrare un prodotto semplicissimo da usare e con una grafica accattivante»**. Questo documento è il riferimento per ogni schermata; `DESIGN.md` dice *cosa* c'è, qui si dice *come appare*. Principio invariato: «alla Apple» — più cura, **non** più funzioni (perimetro v1 di `DESIGN.md` §1-bis).

## 0. Confronto con Navicat (lo screenshot dell'utente)

| Navicat Premium | RamaSQL |
|---|---|
| Barra con 12 icone grandi multicolori sopra il testo, stili diversi tra loro | 10 azioni con **icone lineari coerenti** (stesso tratto, stessa griglia) + etichetta, raggruppate, l'azione principale evidenziata |
| Albero denso, icone piccole di stili diversi, selezione a blocco grigio | Righe ariose (28 px), **intestazioni di sezione** («TABELLE · 6»), selezione a pillola arrotondata con tinta d'accento, conteggi in grigio |
| Griglia fitta: tipo della colonna in piccolo sotto il nome, colori grezzi | Intestazione a due righe curata (nome in grassetto, tipo in grigio con glifo), zebratura quasi invisibile, numeri allineati a destra in cifre tabulari, NULL come etichetta grigia |
| Scuro/chiaro misto, contrasti irregolari | Un solo tema chiaro, calibrato per il **proiettore** (contrasto ≥ 4,5:1 sul testo, ≥ 3:1 su bordi e icone) |
| Nessun racconto di ciò che accade | Ogni modifica ha un **colore di stato** coerente in tutta l'app e l'SQL è sempre in vista |

## 1. Token

Implementati come proprietà FlatLaf in `app/src/main/resources/it/ramasql/app/theme/RamaSqlLaf.properties` (tema derivato da `FlatLightLaf`) e come costanti in `it.ramasql.app.theme.Tokens` per i componenti disegnati a mano. **Nessun colore scritto nel codice fuori da questi due posti.**

### 1.1 Colori

| Token | Valore | Uso |
|---|---|---|
| `bg.window` | `#F6F7F9` | fondo della finestra, zone laterali |
| `bg.surface` | `#FFFFFF` | pannelli di lavoro, griglie, editor, tessere |
| `bg.sunken` | `#EEF1F5` | intestazioni di griglia, barre degli strumenti di scheda, campi di sola lettura |
| `border.subtle` | `#E4E7EC` | divisori, bordi delle celle |
| `border.default` | `#D0D5DD` | bordi di campi e tessere |
| `text.primary` | `#101828` | testo principale |
| `text.secondary` | `#475467` | etichette, tipi di colonna, informazioni |
| `text.tertiary` | `#98A2B3` | segnaposti, NULL, conteggi, suggerimenti |
| `accent` | `#2563EB` | azione principale, selezione, collegamenti, focus |
| `accent.hover` / `accent.pressed` | `#1D4ED8` / `#1E40AF` | stati |
| `accent.tint` | `#EAF1FF` | fondo della selezione, pillole |
| `success` / `success.tint` | `#16A34A` / `#EAF7EE` | connesso, «✔ verificato», righe **nuove** |
| `warning` / `warning.tint` | `#D97706` / `#FFF4DB` | celle **modificate**, avvisi di precheck |
| `danger` / `danger.tint` | `#DC2626` / `#FDECEC` | righe **eliminate**, errori, azioni distruttive |
| `engine.innodb` | `#2563EB` | icona tabella InnoDB |
| `engine.myisam` | `#8B5CF6` | icona tabella MyISAM (viola: distinguibile anche dai daltonici rispetto al blu, con in più il glifo «M») |
| `server.mariadb` | `#0E7490` | pillola del tipo di server (petrolio) |
| `server.mysql` | `#EA580C` | pillola del tipo di server (arancio) |

Sintassi SQL (editor, anteprima, registro): parole chiave `#2563EB` grassetto · stringhe `#B45309` · numeri `#047857` · commenti `#98A2B3` corsivo · funzioni `#7C3AED` · identificatori tra backtick `#101828` · operatori `#475467`. Riga corrente `#F5F8FF`, selezione `#DCE7FF`, errore: sottolineatura ondulata `danger` + fondo `danger.tint` sulla riga.

### 1.2 Tipografia
- Interfaccia: **Segoe UI Variable Text** (Windows 11) → ripiego **Segoe UI**. Codice e griglia numerica: **Cascadia Mono** → ripiego **Consolas**. (Font di sistema: nessun file da distribuire.)
- Scala (px a 100%): `caption 11` · `small 12` · **`body 13`** · `emphasis 13 semibold` · `title 15 semibold` · `heading 18 semibold` · `display 24 semibold` (solo schermata iniziale).
- Cifre **tabulari** nella griglia per i numeri (allineamento verticale perfetto).
- La dimensione del carattere delle impostazioni scala **tutta** la scala in proporzione (proiettore).

### 1.3 Spaziature, raggi, ombre
- Griglia di **4 px**: `4 · 8 · 12 · 16 · 24 · 32 · 48`. Margine interno dei pannelli 16; tra gruppi 24.
- Raggi: `controllo 6` (pulsanti, campi) · `pillola 999` · `tessera 12` · `dialogo 12` · `selezione dell'albero 6`.
- Ombre (solo due livelli): `elev.1` tessere a riposo `0 1 2 rgba(16,24,40,.06)`; `elev.2` tessera al passaggio e menu `0 8 24 rgba(16,24,40,.12)`.
- Altezze: riga dell'albero 28 · riga di griglia 28 · barra strumenti 44 · campi e pulsanti 32 · barra di stato 26.

### 1.4 Movimento
Solo dove aiuta a capire: passaggio del mouse sulle tessere (ombra 120 ms), comparsa del pannello di attesa (fade 150 ms), avanzamento. Nessuna animazione decorativa; tutte disattivabili con le animazioni di sistema.

## 2. Icone
- **Disegnate da noi**, vettoriali (SVG nel pacchetto `it/ramasql/app/icons/`, caricate con `FlatSVGIcon` di FlatLaf Extras, Apache-2.0), nitide a ogni scala. Nessuna immagine di terzi, nessun logo di marchi (MariaDB/MySQL sono indicati da pillole colorate con il nome).
- Griglia 20×20 (barra) e 16×16 (albero, menu); tratto **1,5 px**, estremità arrotondate, angoli r=2; **due toni**: tratto `text.secondary`, riempimento parziale d'accento. Stato disabilitato: 40% di opacità (lo fa FlatLaf).
- Set v1: connetti/disconnetti (spina), nuova query (foglio con `+`), query visiva (tre nodi collegati), nuova tabella (griglia con `+`), nuova vista (occhio sopra griglia), importa (freccia verso il cilindro), esporta/dump (freccia dal cilindro), modello ER (due entità e una relazione), esegui (triangolo pieno d'accento), interrompi (quadrato pieno `danger`); albero: server, catalogo (cilindro), tabella InnoDB, tabella MyISAM (con «M»), vista, colonna, chiave primaria (chiave oro `#CA8A04`), indice (fulmine), chiave esterna (catena), routine (ƒ), trigger (lampo), evento (orologio); griglia: conferma (spunta), scarta (freccia che torna), pagina precedente/successiva, record/griglia; stati: successo, avviso, errore, informazione.

## 3. Componenti

### 3.1 Barra strumenti (finestra principale)
Altezza 44, fondo `bg.window`, nessun bordo 3D. Ogni azione: icona 20 + etichetta `small` accanto, padding 8×12, raggio 6, al passaggio fondo `bg.sunken`. **Gruppi** separati da 16 px (non da linee): *Connessione* · *Crea* (Nuova query, Query visiva, Nuova tabella, Nuova vista) · *Dati* (Importa, Esporta/Dump, Modello ER) · a destra *Esegui* (pulsante pieno `accent`, testo bianco) e *Interrompi* (contorno `danger`, visibile attivo solo durante un'esecuzione). Le azioni non ancora disponibili restano visibili, disabilitate, con suggerimento «Arriva in una prossima versione».

### 3.2 Schermata iniziale (tessere)
Titolo `display` «Ciao! A quale database ti colleghi?» con sottotitolo `text.secondary`. Tessere 260×132, `bg.surface`, raggio 12, bordo `border.subtle`, `elev.1`; al passaggio `elev.2` e bordo `accent`. Contenuto: pillola del server (MariaDB petrolio / MySQL arancio / «Mai connesso» grigia) in alto a sinistra, **nome** `title`, sotto `utente@host:porta` in `text.secondary`, in basso «ultima connessione · versione» in `caption`. Tessera «Nuova connessione» con bordo tratteggiato e `+` grande. Menu «⋯» in alto a destra (Modifica, Duplica, Elimina). Tastiera: frecce tra le tessere, Invio per connettersi.

### 3.3 Navigatore
Fondo `bg.window`. In cima: campo **filtro** con icona lente e segnaposto «Filtra tabelle…», raggio 6. Albero senza linee di collegamento; righe 28; frecce di espansione discrete; **selezione a pillola** `accent.tint` con testo `text.primary` e barra sinistra 2 px `accent` (focus). Sotto il catalogo, intestazioni di sezione in `caption` maiuscoletto `text.tertiary` con conteggio: «TABELLE · 6», «VISTE · 2», «ROUTINE · 1». Colonne: nome + tipo in `text.tertiary` allineato a destra; chiave primaria con chiave oro. Nodo «caricamento…» con spinner piccolo. Stato vuoto: illustrazione lineare + «Nessuna tabella. Creane una con *Nuova tabella*».

### 3.4 Griglia dati (data-entry e risultati)
- Intestazione `bg.sunken`, altezza 44 su **due righe**: nome colonna `emphasis`; sotto, glifo del tipo (chiave per PK, `#` numeri, `Aa` testo, calendario date) + tipo in `caption text.secondary` (es. `VARCHAR(50)` · `NN`). Separatori verticali `border.subtle`.
- Gutter dei numeri di riga 44 px, `caption text.tertiary`, allineati a destra.
- Righe 28; zebratura `#FAFBFC` su righe pari; numeri a destra in cifre tabulari; date centrate; testo a sinistra.
- **Stati** (coerenti in tutta l'app): riga nuova → fondo `success.tint` + barra sinistra 3 px `success` + «＋» nel gutter; cella modificata → fondo `warning.tint` + triangolino `warning` nell'angolo; riga eliminata → fondo `danger.tint`, testo barrato `danger`, «−» nel gutter; cella non valida → bordo 1,5 px `danger` + icona di errore, messaggio come suggerimento; riga in errore dopo la conferma → barra `danger` + messaggio in linea sotto la riga.
- **NULL**: pillola `NULL` in `caption` `text.tertiary` su `bg.sunken` (non la stringa «NULL»). Stringa vuota: cella vuota.
- Selezione a blocco: fondo `accent.tint`, bordo esterno del rettangolo 1,5 px `accent`, cella attiva con bordo pieno.
- Barra della scheda (sopra la griglia): a sinistra paginazione «1–1000 di 12 345» con frecce, a destra **contatore** in pillola (`warning.tint`) «3 inserimenti · 1 modifica · 0 eliminazioni», **Scarta** (secondario) e **Conferma** (primario `accent`, Ctrl+S). Interruttore segmentato «Griglia | Scheda».
- Sola lettura: fascia informativa `accent.tint` con icona «i» e la spiegazione in una riga.

### 3.5 Editor SQL
Fondo `bg.surface`, font mono 13 (segue la scala), numeri di riga `text.tertiary` su `bg.window`, riga corrente `#F5F8FF`, parentesi abbinate con fondo `accent.tint`. Barra sopra: *Esegui istruzione* (primario, Ctrl+Invio), *Esegui tutto*, *Interrompi*, *Apri*, *Salva*. Risultati sotto, in sotto-schede con pillola del numero di righe. Errore: fascia `danger.tint` con codice in grassetto, messaggio originale, spiegazione italiana in `text.secondary`, riga evidenziata nell'editor.

### 3.6 Pannello SQL (Registro · Anteprima · Messaggi)
Schede a sottolineatura (2 px `accent` sulla scheda attiva). Registro come griglia compatta: ora `caption`, **pillola dell'origine** (Editor, Navigatore, Griglia… colori neutri), SQL in mono evidenziato su una riga (a capo al passaggio), esito con icona (spunta `success` / croce `danger`), durata e righe allineate a destra. Pulsante *Esporta registro…* in alto a destra.

### 3.7 Dialoghi
Raggio 12, padding 24, titolo `heading`, testo `body`, al massimo **una decisione** per volta; pulsante primario a destra. **Anteprima SQL** («SQL che verrà eseguito»): riquadro codice `bg.sunken` con evidenziazione, contatore «3 istruzioni», pillola del rischio (`Modifica` ambra, `Distruttiva` rossa). **Conferma rafforzata**: fascia `danger.tint` con icona di avvertimento, frase chiara («Stai per eliminare la tabella **libri** con 200 righe. Non si può annullare.»), campo «Scrivi *libri* per confermare», pulsante *Elimina* `danger` attivo solo quando il nome coincide. **Errori di connessione**: titolo umano («Non riesco a connettermi a …»), cosa correggere in `body`, «Messaggio originale» in un riquadro mono ripiegabile.

### 3.8 Barra di stato
26 px, `bg.window`, bordo superiore `border.subtle`. A sinistra punto verde + «Connesso a *nome*» + `utente@host:porta` in `text.secondary`; a destra pillola del server (petrolio/arancio) con la versione e il catalogo corrente con icona cilindro.

### 3.9 Suggerimenti (tooltip)
Uno stile unico in tutto il programma, pensato per essere **letto**, non intravisto.
- **Forma**: riquadro `bg.surface`, bordo 1 px `border.default`, raggio 6, ombra leggera, padding 10×12; **larghezza massima 360 px** con a-capo automatico (mai una riga lunga che attraversa lo schermo).
- **Contenuto**: in alto il **nome** della cosa in `emphasis` (es. «CASCADE»), sotto il **trafiletto** in `small`, `text.primary`: *cosa fa* → *quando usarlo* → *cosa comporta*. Per le scelte con conseguenze sui dati una riga finale in `warning` («Attenzione: …»). Dove aiuta, l'SQL corrispondente in carattere monospazio (es. `ON DELETE CASCADE`).
- **Tempi**: compare dopo 500 ms di sosta, resta finché il puntatore è sopra (almeno 20 s: il tempo di leggerlo ad alta voce in classe), sparisce appena ci si sposta.
- **Voci delle liste a discesa**: il suggerimento della voce evidenziata compare **accanto** alla lista aperta, allineato alla voce, senza coprire le altre; cambia seguendo il mouse o le frecce della tastiera.
- **Tastiera**: il suggerimento dell'elemento con il focus si apre con **Ctrl+F1** (lo stesso tasto di Workbench e dell'uso comune per «dimmi di più») e si chiude con Esc.
- **Proiettore**: segue la dimensione del carattere scelta nelle impostazioni; se non entra sullo schermo si sposta, non si taglia.
- **Testi**: sempre nei file di risorse (chiavi `<componente>.tooltip` per i componenti, `<lista>.<voce>.tooltip` per le voci delle liste), mai nel codice. Stile di scrittura del §4.
- **Realizzato (Step 12, `ADR-028`)**: `theme.RamaToolTipUI` (disegno), `theme.ComboTips` (voci accanto alla lista), `theme.Tips` (chiavi e testi composti), `theme.KeyTips` (Ctrl+F1). La larghezza massima cresce con il carattere ma resta entro i tre quinti dello schermo; resta a schermo 60 s.

### 3.10 Tastiera, schermi piccoli, barre che si stringono (Step 12, `ADR-028`)
- **Aree**: F6 / Maiusc+F6 fra barra degli strumenti, navigatore, scheda aperta e pannello SQL; nella barra frecce e Spazio. Menu del nodo del navigatore con Maiusc+F10 (o il tasto del menu). Liste nelle celle con Alt+Giù o F4; nella lista aperta le frecce spostano l'evidenziazione (e la spiegazione accanto), Invio sceglie.
- **Schermo**: nessuna finestra esce dallo schermo (1024×768 con il carattere a 28): la parte centrale scorre, i pulsanti restano fissi in basso; i messaggi vanno a capo a 60 caratteri; popup e liste aperte restano dentro lo schermo.
- **Barre**: quando lo spazio manca si stringono prima gli spazi fra i gruppi, poi alcuni pulsanti mostrano la sola icona (il nome diventa il titolo del suggerimento), a gradini (`theme.Compact`); *Conferma* della griglia tiene sempre la scritta, *Esegui* e *Interrompi* la perdono per ultimi.

## 4. Scrittura (ux-copy)
Frasi brevi, in seconda persona, verbi d'azione nei pulsanti («Conferma», «Elimina tabella», «Connetti»), niente gergo tecnico dove non serve, il gergo SQL dove serve (è ciò che si insegna). Errori: *cosa è successo* + *cosa fare*, poi il messaggio originale.

## 5. Accessibilità
Contrasto testo ≥ 4,5:1 (verificato sui token: `text.secondary` su `bg.surface` 7,4:1; `text.tertiary` solo per testo non essenziale), focus sempre visibile (anello 2 px `accent`), tutto raggiungibile da tastiera, stati mai affidati al solo colore (sempre anche un glifo: ＋, −, triangolino, icona).

## 6. Verifica
Ogni schermata ha la sua immagine in `test-results/stepN/` (disegnata dai test `ui`). Controllo a campione con la skill *design-critique* e *accessibility-review*; controllo automatico nei test: nessun colore fuori dai token (ricerca di `new Color(` fuori da `theme/`), contrasto dei token.
