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
public enum Continuation {

    more('+'), done('$'), aborted('#');

    private int delimiter;

    Continuation(int delimiter) {
        this.delimiter = delimiter;
    }


    public int getDelimiter() {
        return delimiter;
    }

    public String toString() {
        switch (delimiter) {
            case '+':
                return "+";
            case '$':
                return "$";
            case '#':
                return "#";
            default:
                return "$";
        }
    }

    public static Continuation parse(byte b) throws IOException {
        if (b == 0x2B) { // '+'
            return more;
        } else if (b == 0x24) { // '$'
            return done;
        } else if (b == 0x23) { // '$'
            return aborted;
        } else {
            throw new IOException("Cannot map byte '" + b + "' to a valid continuation flag");
        }
    }
}
