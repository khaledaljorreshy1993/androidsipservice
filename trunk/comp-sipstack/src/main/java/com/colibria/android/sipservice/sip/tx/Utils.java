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
package com.colibria.android.sipservice.sip.tx;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sip.URI;
import com.colibria.android.sipservice.sip.headers.CSeqHeader;
import com.colibria.android.sipservice.sip.messages.Request;
import com.colibria.android.sipservice.sip.messages.Response;
import com.colibria.android.sipservice.sip.headers.ViaHeader;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 */
public class Utils {
    private static final String TAG = "Utils";
    private static MessageDigest digester;

    private static ThreadLocal<MessageDigest> digesterInstances = new ThreadLocal<MessageDigest>() {
        public MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                Logger.e(TAG, "", e);
                return null;
            }
        }
    };

    static {
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (Exception ex) {
            throw new RuntimeException("Could not intialize Digester ", ex);
        }
    }

    private static java.util.Random rand = new java.util.Random();

    private static long counter = 0;

    /**
     * to hex converter
     */
    private static final char[] toHex = {'0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * convert an array of bytes to an hexadecimal string
     *
     * @param b bytes array to convert to a hexadecimal string
     * @return a string
     */

    public static String toHexString(byte b[]) {
        int pos = 0;
        char[] c = new char[b.length * 2];
        for (byte aB : b) {
            c[pos++] = toHex[(aB >> 4) & 0x0F];
            c[pos++] = toHex[aB & 0x0f];
        }
        return new String(c);
    }

    /**
     * Put quotes around a string and return it.
     *
     * @param str string to be quoted
     * @return a quoted string
     */
    public static String getQuotedString(String str) {
        return '"' + str + '"';
    }

    /**
     * Squeeze out all white space from a string and return the reduced string.
     *
     * @param input input string to sqeeze.
     * @return String a reduced string.
     */
    protected static String reduceString(String input) {
        String newString = input.toLowerCase();
        int len = newString.length();
        String retval = "";
        for (int i = 0; i < len; i++) {
            if (newString.charAt(i) != ' ' && newString.charAt(i) != '\t') {
                retval += newString.charAt(i);
            }
        }
        return retval;
    }

    /**
     * Gives a short textual description of a request
     *
     * @param request the request to describe.
     * @return a String containing the request method, URI and sequence number.
     *         Typically "INVITE sip:espen@paradial.com (cseq 1)"
     */
    public static String getShortDescription(Request request) {
        StringBuffer buf = new StringBuffer(32);
        return buf.append(request.getMethod()).append(" ").append(request.getRequestUri()).
                append("(cseq ").
                append(((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber()).
                append(')').toString();
    }

    /**
     * Gives a short textual description of a response.
     *
     * @param rsp the response to describe.
     * @return a String containing the response code, phrase, and sequence number + method it acks.
     *         Typically "200 OK (cseq 1 INVITE)".
     */
    public static String getShortDescription(Response rsp) {
        StringBuffer buf = new StringBuffer(32);
        return buf.append(rsp.getStatusCode()).append(" ").append(rsp.getReasonPhrase()).
                append("(cseq ").append(((CSeqHeader) rsp.getHeader(CSeqHeader.NAME)).getSeqNumber()).
                append(" ").append(((CSeqHeader) rsp.getHeader(CSeqHeader.NAME)).getMethod()).
                append(")").toString();
    }

    /**
     * Generate a tag for a FROM header or TO header. Just return a random 4 digit integer (should be
     * enough to avoid any clashes!) Tags only need to be unique within a call.
     *
     * @return a string that can be used as a tag parameter.
     */
    public static String generateTag() {
        return Integer.toString((int) (Math.random() * 10000));
    }

    /**
     * Generate a call  identifier. This is useful when we want to generate a call identifier in
     * advance of generating a message.
     *
     * @param uri uri to genereate call id for
     * @return a call identifier
     */
    public static String generateCallIdentifier(URI uri) {
        return generateCallIdentifier(uri.getType() == URI.Type.tel ? uri.getPhonenumber() : uri.getHost());
    }

    /**
     * Generate a call  identifier. This is useful when we want to generate a call identifier in
     * advance of generating a message.
     *
     * @param address address to genereate call id for
     * @return a call identifier
     */
    private static String generateCallIdentifier(String address) {
        String date =
                new StringBuilder().append(Long.toString(System.currentTimeMillis())).append(Double.toString(Math.random())).toString();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte cid[] = messageDigest.digest(date.getBytes());
            String cidString = toHexString(cid);
            return cidString + "@" + address;
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    /**
     * Generate a cryptographically random identifier that can be used to generate a branch
     * identifier.
     *
     * @return a cryptographically random gloablly unique string that can be used as a branch
     *         identifier.
     */
    public static String generateBranchId() {
        long num = ++counter * rand.nextLong() * System.currentTimeMillis();
        byte bid[] = digesterInstances.get().digest(Long.toString(num).getBytes());
        // prepend with a magic cookie to indicate we are bis09 compatible.
        return Constants.BRANCH_MAGIC_COOKIE + toHexString(bid);
    }

    /**
     * The stateless proxy MAY use any technique it likes to guarantee uniqueness of its branch IDs across transactions.
     * However, the following procedure is RECOMMENDED.  The proxy examines the branch ID in the topmost Via header
     * field of the received request.  If it begins with the magic cookie, the first component of the branch ID of the
     * outgoing request is computed as a hash of the received branch ID.
     * Otherwise, the first component of the branch ID is computed as a hash of the topmost Via, the tag in the
     * To header field, the tag in the From header field, the Call-ID header field, the CSeq number (but not method),
     * and the Request-URI from the received request.
     * One of these fields will always vary across two different transactions.
     *
     * @param request
     * @return
     */
    public static String generateBranchIdForStateLessFwd(Request request) {
        ViaHeader via = request.getFirstViaHeader();
        byte bid[] = digesterInstances.get().digest(via.getBranch().getBytes());
        return Constants.BRANCH_MAGIC_COOKIE + toHexString(bid);

    }
}
