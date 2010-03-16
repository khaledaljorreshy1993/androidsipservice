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
 * In case of wildcard, then -1 is used
 *
 * @author Sebastian Dehne
 */
public class ByteRange {
    private static final String WILDCARD = "*";
    private final long start;
    private final long end;
    private final long total;

    protected ByteRange(long start, long end, long total) {
        this.start = start;
        this.end = end;
        this.total = total;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public long getTotal() {
        return total;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(start).append('-');

        if (end >= 0) {
            sb.append(Long.toString(end));
        } else {
            sb.append(WILDCARD);
        }
        sb.append('/');
        if (total >= 0) {
            sb.append(Long.toString(total));
        } else {
            sb.append(WILDCARD);
        }
        return sb.toString();
    }

    public static ByteRange parse(String v) throws IOException {

        long start, end, total;

        /*
         * Parse
         */
        try {
            String startStr = v.substring(0, v.indexOf('-'));
            String endStr = v.substring(v.indexOf('-') + 1, v.indexOf('/'));
            String totalString = v.substring(v.indexOf('/') + 1);

            start = Long.parseLong(startStr);

            if (WILDCARD.equals(endStr)) {
                end = -1;
            } else {
                end = Long.parseLong(endStr);
            }

            if (WILDCARD.equals(totalString)) {
                total = -1;
            } else {
                total = Long.parseLong(totalString);
            }

        } catch (Exception e) {
            throw new IOException("" + e.getMessage());
        }

        /*
         * Validate
         */
        if (start < 1) {
            throw new IOException("Byte-range start field has an invalid value: " + start);
        }

        if (end != -1 && end < 0) {
            throw new IOException("Byte-range end field has an invalid value: " + end);
        }

        if (total != -1 && total < 0) {
            throw new IOException("Byte-range total field has an invalid value: " + total);
        }

        if (total != -1 && end > total) {
            throw new IOException("Byte-Range end field cannot be larger than the total field");
        }

        return new ByteRange(start, end, total);

    }

    public static ByteRange create(long start, long end, long total) {
        return new ByteRange(start, end, total);
    }
}
