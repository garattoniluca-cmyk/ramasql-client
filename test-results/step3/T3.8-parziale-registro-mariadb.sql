-- RamaSQL Client - registro SQL esportato il 2026-09-23 07:45:15
-- Connessione: Test MariaDB (MariaDB 11.5.2)
-- Istruzioni: 4 riuscite; 1 non riuscite o interrotte (commentate, non vengono rieseguite: possono essere state applicate in parte, verifica)
-- Riferimenti al catalogo «<catalogo>» tolti: lo script si riesegue sul catalogo corrente

-- #1 07:45:15 · Editor SQL · OK · 0 righe · 3 ms
CREATE TABLE x (id INT PRIMARY KEY);

-- #2 07:45:15 · Editor SQL · OK · 2 righe · 0 ms
INSERT INTO x VALUES (1), (2);

-- #3 07:45:15 · Editor SQL · OK · 0 righe · 2 ms
CREATE TABLE y (id INT PRIMARY KEY);

-- #4 07:45:15 · Editor SQL · ERRORE 1051 (42S02): (conn=1188) Unknown table '<catalogo>.manca'
-- DROP TABLE x, manca;
-- (non riuscita: può essere stata applicata in parte: verifica sul server prima di rieseguire lo script)

-- #5 07:45:15 · Editor SQL · OK · 1 righe · 0 ms
INSERT INTO y VALUES (7);
