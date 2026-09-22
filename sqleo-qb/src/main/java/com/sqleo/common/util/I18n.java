/*
 * SQLeonardo :: java database frontend
 * Copyright (C) 2004 nickyb@users.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * File contributed by JasperSoft Corp.
 *
 * Modificato per RamaSQL Client (2026-09-21): classe ridotta ai soli metodi usati dal query builder;
 * i testi non si leggono piu' dai ResourceBundle di SQLeo ma dalla facciata it.ramasql.qb.QbHost
 * (QbRuntime.host().text), cosi' l'applicazione fornisce l'italiano. Rimossi: elenco delle lingue,
 * ascoltatori del cambio lingua, scansione del classpath.
 */


package com.sqleo.common.util;

import it.ramasql.qb.QbRuntime;


public class I18n
{
    public static String getString(String cID)
    {
        return QbRuntime.host().text(cID, cID);
    }

    public static String getString(String cID,String defaultValue)
    {
        return QbRuntime.host().text(cID, defaultValue);
    }

    public static String getFormattedString(String cID, String defaultValue, Object[] args)
    {
        String pattern = getString(cID, defaultValue );
        java.text.MessageFormat mf = new java.text.MessageFormat(pattern);
        return mf.format(args);
    }
}
