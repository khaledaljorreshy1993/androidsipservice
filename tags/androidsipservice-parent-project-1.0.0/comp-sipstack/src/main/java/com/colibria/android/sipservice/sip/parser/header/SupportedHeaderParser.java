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
package com.colibria.android.sipservice.sip.parser.header;

import com.colibria.android.sipservice.parse.ByteParser;
import com.colibria.android.sipservice.sip.headers.SupportedHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;

/**
 * @author Sebastian Dehne
 */
public class SupportedHeaderParser implements IHeaderParser<SupportedHeader> {

    private static final ByteParser.Pattern PATTERN_OPTIONTAG;

    static {
        PATTERN_OPTIONTAG = new ByteParser.Pattern();
        PATTERN_OPTIONTAG.setWordCharacter('a', 'z');
        PATTERN_OPTIONTAG.setWordCharacter('A', 'Z');
        PATTERN_OPTIONTAG.setWordCharacter('0', '9');
        PATTERN_OPTIONTAG.setWordCharacter('-');
        PATTERN_OPTIONTAG.setDelimiterCharacter(',');
        PATTERN_OPTIONTAG.setDelimiterCharacter('\r');
    }

    private int lastKnownBufferPosition;
    private int startPosition;
    private String headerName;
    private ByteParser bp = new ByteParser();
    private HashSet<String> tags = new HashSet<String>();

    @Override
    public void reset(String headerName) {
        this.headerName = headerName;
        lastKnownBufferPosition = -1;
        startPosition = -1;
        bp.reset();
        tags.clear();
    }

    @Override
    public SupportedHeader parseMoreData(ByteBuffer bb) throws IOException {
        if (lastKnownBufferPosition == -1) {
            lastKnownBufferPosition = bb.position();
            startPosition = bb.position();
        } else {
            bb.position(lastKnownBufferPosition);
        }

        boolean done = false;

        String tmp;
        int nextChar;
        while (!done) {

            if (bp.read(bb, PATTERN_OPTIONTAG) != ByteParser.READ_WORD) {
                throw new IOException("Unexpected prolog");
            }
            tmp = bp.getWordAsString();
            bp.resetWord();

            nextChar = bp.read(bb, PATTERN_OPTIONTAG);

            if (nextChar == '\r') {
                bb.position(bb.position() - 1);
                done = true;
            } else if (nextChar == ',') {
                tags.add(tmp);
                lastKnownBufferPosition = bb.position();
            }
        }

        return new SupportedHeader(new HashSet<String>(tags));
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public boolean isListedHeader() {
        return false;
    }
}
