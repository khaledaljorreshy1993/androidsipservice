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

import com.colibria.android.sipservice.MimeType;

import java.io.IOException;

/**
 * @author Sebastian Dehne
 */
public class MimeContentTypeHeader implements ICPIMHeader {

    public static final String NAME = "Content-Type";


    private final MimeType mime;

    public MimeContentTypeHeader(MimeType headerValue) {
        mime = headerValue;
    }

    public String getType() {
        return mime.getType();
    }

    public String getSubType() {
        return mime.getSubtype();
    }

    public MimeType getMimeType() {
        return mime;
    }

    public String toString() {
        return mime.toString();
    }

    public static MimeContentTypeHeader parse(String ct) throws IOException {
        // work around for Mercuro client
        if ("*".equals(ct)) {
            return new MimeContentTypeHeader(MimeType.EMPTY);
        }
        MimeType mt = MimeType.parse(ct);
        return new MimeContentTypeHeader(mt);
    }

    public String getName() {
        return NAME;
    }

    public String getValue() {
        return mime.toString();
    }

    public boolean isInMimeSection() {
        return true;
    }
}
