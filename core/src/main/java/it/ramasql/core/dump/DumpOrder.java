/*
 * RamaSQL Client - client didattico per MariaDB e MySQL
 * Copyright (C) 2026 Luca Garattoni
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. This program is
 * distributed WITHOUT ANY WARRANTY. See the LICENSE file for details.
 */
package it.ramasql.core.dump;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * L'ordine in cui gli oggetti di un catalogo si scrivono nel dump, perché lo script si riesegua senza errori:
 * <ul>
 *   <li>prima le <b>tabelle</b>, poi le <b>viste</b> (una vista legge tabelle che devono già esistere);</li>
 *   <li>fra le tabelle, prima quelle <b>riferite</b> dalle chiavi esterne ({@code editori} prima di {@code libri});</li>
 *   <li>fra le viste, prima quelle usate da <b>altre viste</b> ({@code v_prestiti_aperti} prima di {@code v_riepilogo});</li>
 *   <li>chiavi esterne <b>circolari</b> (A → B → A): nessun ordine va bene, quindi il dump spegne i controlli delle chiavi
 *       esterne durante il ripristino ({@code SET FOREIGN_KEY_CHECKS=0}) anche se l'opzione non è scelta;</li>
 *   <li>una tabella che si riferisce a <b>sé stessa</b> ({@code dipendenti.responsabile → dipendenti.id}): le righe
 *       escono in ordine di chiave, ma un responsabile può avere un {@code id} più alto del dipendente, quindi anche qui
 *       i controlli delle chiavi esterne si spengono durante il ripristino.</li>
 * </ul>
 * A parità, ordine alfabetico: due dump dello stesso catalogo sono uguali.
 *
 * @param tables       tabelle nell'ordine di scrittura
 * @param views        viste nell'ordine di scrittura
 * @param circularKeys   le tabelle con chiavi esterne circolari (vuoto = nessuna)
 * @param selfReferences le tabelle con una chiave esterna verso sé stesse
 */
public record DumpOrder(List<String> tables, List<String> views, List<String> circularKeys,
        List<String> selfReferences) {

    public DumpOrder {
        tables = List.copyOf(tables);
        views = List.copyOf(views);
        circularKeys = List.copyOf(circularKeys);
        selfReferences = List.copyOf(selfReferences);
    }

    /** Le chiavi esterne formano un ciclo fra tabelle diverse. */
    public boolean circular() {
        return !circularKeys.isEmpty();
    }

    /** Il ripristino richiede {@code FOREIGN_KEY_CHECKS=0}: chiavi circolari o tabelle che si riferiscono a sé stesse. */
    public boolean needsForeignKeysOff() {
        return circular() || !selfReferences.isEmpty();
    }

    /**
     * @param references per ogni tabella, le tabelle (dello stesso catalogo) che le sue chiavi esterne riferiscono
     * @param views      per ogni vista, la sua definizione (per trovare le viste che usa)
     */
    public static DumpOrder of(Map<String, ? extends Collection<String>> references, Map<String, String> views) {
        Map<String, Set<String>> tableDeps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> self = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, ? extends Collection<String>> e : references.entrySet()) {
            Set<String> deps = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String r : e.getValue()) {
                if (r.equalsIgnoreCase(e.getKey())) {
                    self.add(e.getKey());   // non impone un ordine fra tabelle, ma fra le righe sì
                } else if (references.keySet().stream().anyMatch(r::equalsIgnoreCase)) {
                    deps.add(r);
                }
            }
            tableDeps.put(e.getKey(), deps);
        }
        List<String> circular = new ArrayList<>();
        List<String> tables = sort(tableDeps, circular);

        Map<String, Set<String>> viewDeps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> v : views.entrySet()) {
            Set<String> deps = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String other : views.keySet()) {
                if (!other.equalsIgnoreCase(v.getKey()) && mentions(v.getValue(), other)) {
                    deps.add(other);
                }
            }
            viewDeps.put(v.getKey(), deps);
        }
        List<String> ignored = new ArrayList<>();
        List<String> orderedViews = sort(viewDeps, ignored);
        return new DumpOrder(tables, orderedViews, circular, List.copyOf(self));
    }

    /** La definizione nomina l'oggetto: {@code `nome`} tra backtick o come parola intera. */
    static boolean mentions(String definition, String name) {
        if (definition == null) {
            return false;
        }
        if (definition.toLowerCase(Locale.ROOT).contains("`" + name.toLowerCase(Locale.ROOT) + "`")) {
            return true;
        }
        return Pattern.compile("(?<![\\w`$])" + Pattern.quote(name) + "(?![\\w`$])", Pattern.CASE_INSENSITIVE)
                .matcher(definition).find();
    }

    /** Ordinamento topologico stabile (Kahn, alfabetico a parità); i nodi di un ciclo vanno in fondo, in {@code cycle}. */
    private static List<String> sort(Map<String, Set<String>> deps, List<String> cycle) {
        List<String> out = new ArrayList<>();
        Set<String> done = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<String, Set<String>> e : deps.entrySet()) {
                if (!done.contains(e.getKey()) && done.containsAll(e.getValue())) {
                    out.add(e.getKey());
                    done.add(e.getKey());
                    progress = true;
                    break;   // si ricomincia dal primo in ordine alfabetico
                }
            }
        }
        for (String name : deps.keySet()) {
            if (!done.contains(name)) {
                out.add(name);
                cycle.add(name);
            }
        }
        return out;
    }
}
