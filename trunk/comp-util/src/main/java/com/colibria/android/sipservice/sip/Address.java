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
package com.colibria.android.sipservice.sip;

import com.colibria.android.sipservice.NameValuePair;
import com.colibria.android.sipservice.logging.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class Address implements Comparable<Address>, Serializable {

    private static final String TAG = "Address";

    public static Address fromString(String input) {
        try {
            AddressParser a = new AddressParser();
            a.reset(null, false);
            a.parseMoreData(ByteBuffer.wrap((input + "\r").getBytes()));
            return new Address(a.getUri(), a.getDisplayName(), a.getParameters());
        } catch (IOException e) {
            Logger.e(TAG, "Parse error", e);
        }
        return null;
    }

    private transient volatile URI uri;
    private transient volatile String displayName;
    private transient volatile List<NameValuePair> parameters;
    private volatile String serializedTempString;

    public Address(URI uri, String displayName, List<NameValuePair> parameters) {
        this.uri = uri;
        this.displayName = displayName;
        if (parameters != null)
            this.parameters = Collections.unmodifiableList(parameters);
        else
            this.parameters = Collections.emptyList();
    }

    public URI getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<NameValuePair> getParameters() {
        return parameters;
    }

    public boolean isParameterSet(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equals(parameterName))
                return true;
        }
        return false;
    }

    public boolean isParameterSetCaseInsensitive(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equalsIgnoreCase(parameterName))
                return true;
        }
        return false;
    }

    public String getParameterValue(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equals(parameterName))
                return p.getValue();
        }
        return null;
    }

    public String getParameterValueCaseInsensitive(String parameterName) {
        for (NameValuePair p : parameters) {
            if (p.getName().equalsIgnoreCase(parameterName))
                return p.getValue();
        }
        return null;
    }

    public void writeValueToBuffer(OutputStream bb) throws IOException {
        if (displayName != null) {
            bb.write(displayName.getBytes());
            if (displayName.length() > 0) {
                bb.write(" ".getBytes());
            }
            bb.write("<".getBytes());
            bb.write(uri.toString().getBytes());
            bb.write(">".getBytes());

            if (parameters.size() > 0) {
                for (NameValuePair p : parameters) {
                    bb.write(';');
                    p.writeValueToBuffer(bb);
                }
            }

        } else {
            bb.write(uri.toString().getBytes());
        }

    }

    public String toString() {
        OutputStream os = new ByteArrayOutputStream(1024);
        try {
            writeValueToBuffer(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return os.toString();
    }


    @Override
    public int compareTo(Address o) {
        URI localUri, otherUri = null;
        if ((localUri = this.uri) != null && (otherUri = o.uri) != null) {
            return localUri.toLowerCaseStringWithoutParams().compareTo(otherUri.toLowerCaseStringWithoutParams());
        }

        if (localUri == null && otherUri == null)
            return 0;

        if (localUri != null && otherUri == null) {
            return 1;
        } else {
            return -1;
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        serializedTempString = toString();
        out.defaultWriteObject();
        serializedTempString = null;
    }


    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Address a = fromString(serializedTempString);
        this.displayName = a.displayName;
        this.uri = a.uri;
        this.parameters = a.parameters;
        serializedTempString = null;
    }
}
