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

/**
 * @author Sebastian Dehne
 */
public class StatusHeader {

    public static final String NAME = "Status";

    private final int code;
    private final String reason;

    private StatusHeader(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("000 ");
        sb.append(code);
        if (reason != null) {
            sb.append(' ').append(reason);
        }
        return sb.toString();
    }

    public static StatusHeader parse(String v) throws IOException {
        /*
         * possible formats are:
         *   '000 100 Comment'
         *   or
         *   '000 100'       
         */
        try {
            String[] chunks = v.split(" ", 3);
            int code = Integer.parseInt(chunks[1]);

            return new StatusHeader(code, chunks.length > 2 ? chunks[2] : null);
        } catch (Exception e) {
            throw new IOException("Could not parse status header " + e.getMessage());
        }

    }
}
