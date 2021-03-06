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
package com.colibria.android.sipservice.sip.headers;

import com.colibria.android.sipservice.MimeType;

import java.io.OutputStream;
import java.io.IOException;

/**
 * @author Sebastian Dehne
 */
public abstract class MimeTypeBasedHeader extends SipHeader {

    private final MimeType mimeType;

    protected MimeTypeBasedHeader(String name, MimeType mimeType) {
        super(name);
        this.mimeType = mimeType;
    }

    public MimeType getMimeType() {
        return mimeType;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(mimeType.toString().getBytes());
    }

}
