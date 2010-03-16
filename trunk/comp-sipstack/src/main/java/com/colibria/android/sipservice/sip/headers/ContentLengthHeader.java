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

import java.io.OutputStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: sebas
 * Date: Dec 9, 2009
 * Time: 11:59:52 AM
 * To change this template use File | Settings | File Templates.
 */
public class ContentLengthHeader extends SipHeader {

    public static final String NAME = "Content-Length";
    public static final String NAME_SHORT = "l";

    private final int length;

    public ContentLengthHeader(int length) {
        super(NAME);
        this.length = length;
    }

    public int getContentLength() {
        return length;
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        bb.write(Integer.toString(length).getBytes());
    }
}
