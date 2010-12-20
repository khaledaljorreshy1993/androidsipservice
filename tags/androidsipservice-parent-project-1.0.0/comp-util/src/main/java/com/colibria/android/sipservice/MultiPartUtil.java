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

import com.colibria.android.sipservice.logging.Logger;
import org.w3c.www.mime.MimeHeaders;
import org.w3c.www.mime.MimeHeadersFactory;
import org.w3c.www.mime.MimeParser;
import org.w3c.www.mime.MultipartInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 */
public class MultiPartUtil {
    private static final String TAG = "MultiPartUtil";

    public static class Content {

        public Content(MimeType type, byte[] content) {
            this.type = type;
            this.content = content;
        }

        public final MimeType type;
        public final byte[] content;
    }

    public static List<Content> extractMultiparts(byte[] body, byte[] boundary) {

        List<Content> result = new LinkedList<Content>();
        MimeHeadersFactory headFactory = new MimeHeadersFactory();
        MimeParser mp = new MimeParser(new ByteArrayInputStream(body), headFactory);

        MultipartInputStream mIS = new MultipartInputStream(mp.getInputStream(), boundary);
        try {
            while (mIS.nextInputStream()) {
                if (mIS.available() > 0) {
                    // create parser for each part in multipart
                    MimeParser partParser = new MimeParser(mIS, headFactory);
                    MimeHeaders mh = (MimeHeaders) partParser.parse();
                    MimeType type = MimeType.parse(mh.getValue("Content-Type"));
                    ByteArrayOutputStream os = new ByteArrayOutputStream(body.length);
                    byte[] buf = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = mIS.read(buf, 0, buf.length)) > 0) {
                        os.write(buf, 0, bytesRead);
                    }
                    result.add(new Content(type, os.toByteArray()));
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return result;
    }

    /**
     * Searched all mime part and returns the first part found
     *
     * @param contentType the content type to search for
     * @param body        the entire multiplart block
     * @param boundary    the boundary
     * @return the body part, if found
     */
    public static byte[] getContent(MimeType contentType, byte[] body, byte[] boundary) {

        MimeHeadersFactory headFactory = new MimeHeadersFactory();
        MimeParser mp = new MimeParser(new ByteArrayInputStream(body), headFactory);

        MultipartInputStream mIS = new MultipartInputStream(mp.getInputStream(), boundary);
        try {
            while (mIS.nextInputStream()) {
                if (mIS.available() > 0) {
                    // create parser for each part in multipart
                    MimeParser partParser = new MimeParser(mIS, headFactory);
                    MimeHeaders mh = (MimeHeaders) partParser.parse();
                    if (contentType.toString().equalsIgnoreCase(mh.getValue("Content-Type"))) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream(body.length);
                        byte[] buf = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = mIS.read(buf, 0, buf.length)) > 0) {
                            os.write(buf, 0, bytesRead);
                        }
                        return os.toByteArray();
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }

    public static byte[] createMultiPartBody(String boundary, MultiPartBody... parts) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            for (MultiPartBody p : parts) {
                os.write(("--" + boundary + "\r\n").getBytes());
                if (p.headers != null && p.headers.size() > 0) {
                    for (NameValuePair nvp : p.headers) {
                        os.write((nvp.getName() + ": " + nvp.getValue() + "\r\n").getBytes());
                    }
                }
                os.write("\r\n".getBytes());
                os.write(p.content);
                os.write("\r\n".getBytes());
            }
            os.write(("--" + boundary + "--\r\n").getBytes());
            return os.toByteArray();
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }

    public static class MultiPartBody {
        List<NameValuePair> headers;
        byte[] content;

        public MultiPartBody(List<NameValuePair> headers, byte[] content) {
            this.headers = headers;
            this.content = content;
        }
    }
}
