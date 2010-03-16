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
package com.colibria.android.sipservice;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Sebastian Dehne
 */
public class ByteBufferOutputStream extends OutputStream {

    private final ByteBuffer bb;

    public ByteBufferOutputStream(ByteBuffer bb) {
        this.bb = bb;
    }

    @Override
    public void write(int b) throws IOException {
        if (b >= 0 && b < 256)
            bb.put((byte) b);
        else
            throw new IllegalArgumentException("b is not a valid byte " + b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        bb.put(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        bb.put(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        // ignore
    }

    @Override
    public void close() throws IOException {
        //ignore
    }

    public ByteBuffer getBb() {
        return bb;
    }
}
