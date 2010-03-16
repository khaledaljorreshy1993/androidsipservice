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
package com.colibria.android.sipservice.sip.messages;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.MultiPartUtil;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.sdp.api.SessionDescription;
import com.colibria.android.sipservice.sdp.parser.SDPAnnounceParser;
import com.colibria.android.sipservice.sip.headers.*;
import com.colibria.android.sipservice.sip.tx.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.*;

/**
 * @author Sebastian Dehne
 */
public abstract class SipMessage {
    private static final String TAG = "SipMessage";

    protected static final String COLON = ":";

    /**
     * Set of target refresh methods, currently: INVITE, UPDATE, SUBSCRIBE,
     * NOTIFY, REFER
     * <p/>
     * A target refresh request and its response MUST have a Contact
     */
    private static final Set<String> targetRefreshMethods = new HashSet<String>();

    static {
        targetRefreshMethods.add(Invite.NAME);
        targetRefreshMethods.add(Subscribe.NAME);
        targetRefreshMethods.add(Notify.NAME);
        targetRefreshMethods.add(Refer.NAME);
    }

    public static boolean isTargetRefresh(String ucaseMethod) {
        return targetRefreshMethods.contains(ucaseMethod);
    }

    public static final String SESSION_TIMER_REFRESHER = "refresher";

    public static enum SessionTimerRefresher {
        uas, uac
    }

    private SessionDescription sdp = null;
    protected final HashMap<String, List<SipHeader>> headers;
    private final byte[] body;

    protected SipMessage(HashMap<String, List<SipHeader>> headers, byte[] body) {
        if (headers == null) {
            this.headers = new HashMap<String, List<SipHeader>>();
        } else
            this.headers = headers;
        this.body = body;
        setHeader(new ContentLengthHeader(this.body != null ? this.body.length : 0));
    }

    public byte[] getBody() {
        return body;
    }

    public void addHeader(SipHeader h) {
        List<SipHeader> tmp = headers.get(h.getName());
        if (tmp == null) {
            tmp = new LinkedList<SipHeader>();
            headers.put(h.getName(), tmp);
        }
        tmp.add(h);
    }

    public <H extends SipHeader> void addHeaders(List<H> rhIter) {
        for (SipHeader h : rhIter) {
            addHeader(h);
        }
    }

    public void removeHeaders(String name) {
        headers.remove(name);
    }

    public void setHeader(SipHeader h) {
        LinkedList<SipHeader> tmp = new LinkedList<SipHeader>();
        tmp.add(h);
        headers.put(h.getName(), tmp);
    }

    public ViaHeader getFirstViaHeader() {
        return (ViaHeader) getHeader(ViaHeader.NAME);
    }

    public String getCallId() {
        CallIDHeader h = (CallIDHeader) getHeader(CallIDHeader.NAME);
        if (h != null) {
            return h.getCallId();
        }
        return null;
    }

    public FromHeader getFrom() {
        return (FromHeader) getHeader(FromHeader.NAME);
    }

    public ToHeader getTo() {
        return (ToHeader) getHeader(ToHeader.NAME);
    }

    public String getToTag() {
        ToHeader th = getTo();
        return th != null ? th.getTag() : null;
    }

    public String getFromTag() {
        FromHeader fh = getFrom();
        return fh != null ? fh.getTag() : null;
    }

    public <T extends SipHeader> T getHeader(String name) {
        List<? extends SipHeader> tmp = headers.get(name);
        if (tmp != null && tmp.size() > 0) {
            return (T) tmp.get(0);
        }
        return null;
    }

    public <T extends SipHeader> List<T> getHeaders(String name) {
        //noinspection unchecked
        return (List<T>) headers.get(name);
    }

    public CSeqHeader getCSeq() {
        List<SipHeader> tmp = headers.get(CSeqHeader.NAME);
        if (tmp != null && tmp.size() > 0) {
            return (CSeqHeader) tmp.get(0);
        }
        return null;
    }

    public String getTransactionId(boolean skipPrependCancel) {

        ViaHeader topVia = getFirstViaHeader();

        // Have specified a branch Identifier so we can use it to identify
        // the transaction. BranchId is not case sensitive.
        // Branch Id prefix is not case sensitive.
        if (topVia.getBranch() != null && topVia.getBranch().toUpperCase().startsWith(Constants.BRANCH_MAGIC_COOKIE_UPPER_CASE)) {
            // Bis 09 compatible branch assignment algorithm.
            // implies that the branch id can be used as a transaction
            // identifier.
            if (this.getCSeq().getMethod().equals(Cancel.NAME) && !skipPrependCancel)
                return (topVia.getBranch() + ":" + this.getCSeq().getMethod()).toLowerCase();
            else
                return topVia.getBranch().toLowerCase();
        } else {
            // old style not supported
            return null;
        }
    }

    public String toString() {
        // should only be used for debugging
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 8);
        try {
            writeToBuffer(os);
        } catch (IOException e) {
            //doesn't happen
        }

        return new String(os.toByteArray());
    }

    public void writeToBuffer(OutputStream bb) throws IOException {
        writeFirstLineToBuffer(bb);

        for (Map.Entry<String, List<SipHeader>> e : headers.entrySet()) {
            boolean isListedHeader = false;
            if (ViaHeader.NAME.equals(e.getKey()) || ViaHeader.NAME_SHORT.equals(e.getKey())) {
                isListedHeader = true;
            }

            boolean headerNamePrinted = false;
            for (SipHeader h : e.getValue()) {
                if (isListedHeader && headerNamePrinted) {
                    bb.write(",".getBytes());
                }
                h.writeToBuffer(bb, !headerNamePrinted);
                if (!isListedHeader) {
                    bb.write("\r\n".getBytes());
                    headerNamePrinted = false;
                } else {
                    headerNamePrinted = true;
                }
            }

            if (isListedHeader) {
                bb.write("\r\n".getBytes());
            }
        }
        bb.write("\r\n".getBytes());
        if (body != null) {
            bb.write(body);
        }
    }

    public String getDialogId(boolean isServer, String toTag) {
        String tmp;

        StringBuffer retval = new StringBuffer(getCallId());
        if (!isServer) {
            if ((tmp = getFromTag()) != null) {
                retval.append(COLON);
                retval.append(tmp);
            }
            if ((tmp = toTag == null ? getToTag() : toTag) != null) {
                retval.append(COLON);
                retval.append(tmp);
            }
        } else {
            if ((tmp = toTag == null ? getToTag() : toTag) != null) {
                retval.append(COLON);
                retval.append(tmp);
            }
            if ((tmp = getFromTag()) != null) {
                retval.append(COLON);
                retval.append(tmp);
            }
        }
        return retval.toString().toLowerCase();
    }

    public void addSupportedOptionTag(Collection<String> s) {
        for(String st : s) {
            addSupportedOptionTag(st);
        }
    }

    public void addSupportedOptionTag(String s) {
        SupportedHeader supportedHeader = getHeader(SupportedHeader.NAME);
        if (supportedHeader == null) {
            supportedHeader = new SupportedHeader(new HashSet<String>());
            setHeader(supportedHeader);
        }
        supportedHeader.add(s);
    }

    public void validate() throws IOException {
        if (!headers.containsKey(FromHeader.NAME)) {
            throw new IOException("No From header found");
        }

        if (!headers.containsKey(ToHeader.NAME)) {
            throw new IOException("No From header found");
        }

        if (!headers.containsKey(CallIDHeader.NAME)) {
            throw new IOException("No Call-ID header found");
        }
        if (!headers.containsKey(ViaHeader.NAME)) {
            throw new IOException("No ViaHeader header found");
        }

        ContentLengthHeader clh = getHeader(ContentLengthHeader.NAME);
        if (clh == null) {
            throw new IOException("No Content-Length header found");
        }

        if (clh.getContentLength() > 0) {
            if (!headers.containsKey(ContentTypeHeader.NAME)) {
                throw new IOException("No ContentType header found");
            }

            if (body == null || body.length == 0) {
                throw new IOException("No body found, but Content-length was greater than 0");
            }
        } else {
            if (body != null && body.length > 0) {
                throw new IOException("Body found, but Content-length was 0");
            }
        }

    }

    /**
     * Get's the SDP out of the body and returns it as a string. The answer is cached.
     * Once getSdp() is invoked, the cached string is cleared once and for all,
     * and this method will return getSdp().toString() from then on for each repeated
     * call.
     *
     * @return the SDP as a String
     */
    public synchronized String getSdpAsString() {

        // SDP already parsed?
        if (sdp != null) {
            return sdp.toString();
        } else {
            ContentTypeHeader contentTypeHeader = getHeader(ContentTypeHeader.NAME);
            if (body != null && body.length > 0 && contentTypeHeader != null) {
                if (MimeType.APPLICATION_SDP.match(contentTypeHeader.getMimeType()) == MimeType.MATCH_SPECIFIC_SUBTYPE) {
                    return new String(body);
                } else if (MimeType.MULTIPART_MIXED.match(contentTypeHeader.getMimeType()) == MimeType.MATCH_SPECIFIC_SUBTYPE
                        || MimeType.MULTIPART_RELATED.match(contentTypeHeader.getMimeType()) == MimeType.MATCH_SPECIFIC_SUBTYPE) {
                    byte[] tmp = MultiPartUtil.getContent(MimeType.APPLICATION_SDP, body, contentTypeHeader.getMimeType().getParameterValue("boundary").getBytes());
                    return new String(tmp);
                }
            }
        }
        return null;
    }

    public SessionDescription getSdp() {
        if (sdp != null) {
            return sdp;
        }
        String s;
        if ((s = getSdpAsString()) != null) {
            SDPAnnounceParser sdpParser = new SDPAnnounceParser(s);
            try {
                sdp = sdpParser.parse();
            } catch (ParseException e) {
                Logger.e(TAG, "", e);
                return null;
            }
            return sdp;
        } else {
            return null;
        }

    }

    protected abstract void writeFirstLineToBuffer(OutputStream bb) throws IOException;
}
