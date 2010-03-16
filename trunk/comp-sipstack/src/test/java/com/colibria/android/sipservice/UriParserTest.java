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

import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.UriParser;
import junit.framework.TestCase;

import java.io.EOFException;
import java.nio.ByteBuffer;

/**
 * @author Sebastian Dehne
 */
public class UriParserTest extends TestCase {

    private void readMoreBytes(ByteBuffer dst, byte[] data, int bytesToBeAdded) {
        int alreadyRead = dst.limit();
        dst.position(dst.limit());
        dst.limit(dst.capacity());

        System.arraycopy(data, alreadyRead, dst.array(), dst.arrayOffset() + alreadyRead, bytesToBeAdded);
        dst.position(alreadyRead + bytesToBeAdded);
    }

    @SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
    public void testUriParser() throws Exception {
        byte[] data = "sip:username:password@hostname.com:5060;param1;param2=value ".getBytes();
        ByteBuffer bb = ByteBuffer.allocate(1024 * 8);
        bb.flip();
        URI result;
        UriParser uriParser = new UriParser();
        boolean EOFFound;


        readMoreBytes(bb, data, 1);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 3);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 18);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 18);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 7);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 7);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(EOFFound);

        readMoreBytes(bb, data, 6);
        bb.flip();
        EOFFound = false;
        try {
            result = uriParser.parseMore(bb);
        } catch (EOFException e) {
            EOFFound = true;
        }
        assertTrue(!EOFFound);
    }

    public void testUriParser2() throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(1024 * 8);
        bb.flip();
        URI u;
        UriParser uriParser = new UriParser();

        byte[] uri1 = "sip:username:password@hostname.com:5060;param1;param2=value ".getBytes();
        readMoreBytes(bb, uri1, uri1.length);
        bb.flip();
        u = uriParser.parseMore(bb);
        assertTrue(u.getType() == URI.Type.sip);
        assertEquals(u.getHost(), "hostname.com");
        assertEquals(u.getPort(), 5060);
        assertEquals(u.getUsername(), "username");
        assertEquals(u.getPassword(), "password");
        assertEquals(u.isParameterSet("param1"), true);
        assertEquals(u.isParameterSet("param2"), true);
        assertEquals(u.getParameterValue("param2"), "value");
        bb.clear();
        bb.flip();
        uriParser.reset();

        byte[] uri2 = "tel:+1234;name1;name2=value ".getBytes();
        readMoreBytes(bb, uri2, uri2.length);
        bb.flip();
        u = uriParser.parseMore(bb);
        assertEquals(u.getType(), URI.Type.tel);
        assertEquals(u.getPhonenumber(), "+1234");
        assertEquals(u.isParameterSet("name1"), true);
        assertEquals(u.isParameterSet("name2"), true);
        assertEquals(u.getParameterValue("name2"), "value");
        bb.clear();
        bb.flip();
        uriParser.reset();

        byte[] uri3 = "sip:username:password@hostname.com\r\n".getBytes();
        readMoreBytes(bb, uri3, uri3.length);
        bb.flip();
        u = uriParser.parseMore(bb);
        bb.clear();
        bb.flip();
        uriParser.reset();

    }

    public void testAddressParser() throws Exception {
        String input = "Bob the builder <sip:myself@example.com:5060>;tag=1234";
        Address address = Address.fromString(input);
        assertTrue(address != null);
        assertTrue(address.getDisplayName().equals("Bob the builder"));
        assertTrue(address.getUri() != null);
        assertTrue(address.getUri().getUsername().equals("myself"));
        assertTrue(address.isParameterSet("tag"));
    }
}
