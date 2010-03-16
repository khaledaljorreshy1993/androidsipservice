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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sebatsian Dehne
 */
public class ContentDispositionHeader implements ICPIMHeader {

    public static final String NAME = "Content-Disposition";

    private final boolean isInMimeSection;
    private final String type;
    private final Map<String, String> parameters;

    private ContentDispositionHeader(boolean isInMimeSection, String type, Map<String, String> parameters) {
        this.type = type;
        this.parameters = Collections.unmodifiableMap(parameters);
        this.isInMimeSection = isInMimeSection;
    }

    public String getName() {
        return NAME;
    }

    public String getValue() {
        StringBuffer sb = new StringBuffer();
        sb.append(type);
        for (String key : parameters.keySet()) {
            sb.append(";").append(key).append("=").append(parameters.get(key));
        }
        return sb.toString();
    }

    public boolean isInMimeSection() {
        return isInMimeSection;
    }

    public String toString() {
        return getValue();
    }

    public String getType() {
        return type;
    }

    public static ContentDispositionHeader getForFileTransfer(String filename, long fileSize) {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("filename", filename);
        parameters.put("size", Long.toString(fileSize));

        return new ContentDispositionHeader(true, "attachment", parameters);
    }

    public static ContentDispositionHeader getForRenderTextMsg() {
        return new ContentDispositionHeader(true, "inline", new HashMap<String, String>());
    }

    public static ContentDispositionHeader getForRenderImageFile(String filename, long fileSize) {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("filename", filename);
        parameters.put("size", Long.toString(fileSize));

        return new ContentDispositionHeader(true, "inline", parameters);
    }


    public static ContentDispositionHeader parse(String headerValue, boolean isInMimeSection) throws IOException {

        try {
            String[] chunks = headerValue.split(";");
            String type = chunks[0].trim();

            Map<String, String> parameters = new HashMap<String, String>();
            String[] tmp;
            // todo check if there are parameters without a value
            for (int i = 1; i < chunks.length; i++) {
                tmp = chunks[i].split("=");
                parameters.put(tmp[0].trim(), tmp[1].trim());
            }

            return new ContentDispositionHeader(isInMimeSection, type, parameters);
        } catch (Exception e) {
            throw new IOException("Could not parse header " + e.getMessage());
        }
    }

    public String getParameterValue(String s) {
        if (parameters != null) {
            return parameters.get(s);
        }
        return null;
    }
}
