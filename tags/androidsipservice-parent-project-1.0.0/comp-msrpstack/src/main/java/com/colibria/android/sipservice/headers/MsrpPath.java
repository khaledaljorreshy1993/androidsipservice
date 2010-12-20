/*
 *
 * Copyright (C) 2010 Colibria AS
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.android.sipservice.headers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class MsrpPath {

    private final List<MsrpURI> uris;

    public MsrpPath(MsrpURI uri) {
        List<MsrpURI> uris = new LinkedList<MsrpURI>();
        uris.add(uri);
        this.uris = Collections.unmodifiableList(uris);
    }

    public MsrpPath(List<MsrpURI> uris) {
        this.uris = uris;
    }

    public List<MsrpURI> getURIs() {
        return uris;
    }

    public MsrpURI getFirst() {
        return uris.get(0);
    }

    public static MsrpPath parseValue(String value) throws IOException {

        List<MsrpURI> list = new ArrayList<MsrpURI>();

        String[] uris = value.split(" ");
        for (String uri : uris) {
            list.add(MsrpURI.parse(uri));
        }

        return new MsrpPath(Collections.unmodifiableList(list));
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        boolean cont = false;
        for (MsrpURI uri : uris) {
            if (cont) {
                sb.append(" ");
            } else {
                cont = true;
            }
            sb.append(uri);
        }
        return sb.toString();

    }
}
