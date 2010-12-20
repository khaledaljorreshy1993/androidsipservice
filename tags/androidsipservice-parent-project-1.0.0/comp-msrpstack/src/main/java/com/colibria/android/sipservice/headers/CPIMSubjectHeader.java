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
public class CPIMSubjectHeader implements ICPIMHeader {
    public static final String NAME = "Subject";

    private final String subject;
    private final String lang;

    private CPIMSubjectHeader(String subject, String lang) {
        this.subject = subject;
        this.lang = lang;
    }

    public String getSubject() {
        return subject;
    }

    public String getLang() {
        return lang;
    }

    public static CPIMSubjectHeader parse(String value) throws IOException {
        // possible formats are:
        //    ;lang=en A subject with language indication
        //  or
        //   A subject without language indication

        try {
            String lang;
            String subject;
            if (value.charAt(0) == ';') {
                String[] tmp = value.substring(1).split(" ", 2);
                subject = tmp[1];
                tmp = tmp[0].split("=", 2);
                lang = tmp[1];
            } else {
                lang = null;
                subject = value;
            }

            return new CPIMSubjectHeader(subject, lang);
        } catch (Exception e) {
            throw new IOException("Could not create subject header " + e.getMessage());
        }
    }

    public String getName() {
        return NAME;
    }

    public String getValue() {
        return subject; //todo lang
    }

    public boolean isInMimeSection() {
        return false;
    }
}
