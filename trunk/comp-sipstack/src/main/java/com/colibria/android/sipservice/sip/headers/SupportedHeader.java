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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

/**
 * @authorSebastian Dehne
 */
public class SupportedHeader extends SipHeader {

    public static final String NAME = "Supported";
    public static final String NAME_SHORT = "k";

    private final Set<String> optionTags;

    public SupportedHeader(Set<String> optionTags) {
        super(NAME);
        this.optionTags = optionTags;
    }

    public boolean isOptionTagSet(String tag) {
        return optionTags.contains(tag);
    }

    public void add(String tag) {
        optionTags.add(tag);
    }

    public void remove(String tag) {
        optionTags.remove(tag);
    }

    @Override
    protected void writeValueToBuffer(OutputStream bb) throws IOException {
        boolean firstDone = false;
        for (String tags : optionTags) {
            if (firstDone) {
                bb.write(",".getBytes());
            } else {
                firstDone = true;
            }
            bb.write(tags.getBytes());
        }
    }
}
