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
public class FailureReportHeader {

    public enum Value {
        yes, no, partial;

        public static Value fromValue(String value) throws IOException {
            for (Value rp : values()) {
                if (rp.toString().equalsIgnoreCase(value)) {
                    return rp;
                }
            }
            throw new IOException("invalid report header " + value);
        }

    }

    private final Value value;

    public FailureReportHeader(String value) throws IOException {
        this.value = Value.fromValue(value);
    }

    public Value get() {
        return value;
    }

    public String toString() {
        return value.toString();
    }
}
