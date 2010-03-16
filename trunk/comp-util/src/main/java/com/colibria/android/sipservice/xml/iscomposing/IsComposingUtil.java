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
package com.colibria.android.sipservice.xml.iscomposing;

import com.colibria.android.sipservice.MimeType;
import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.xml.ChangedKXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Sebastian Dehne
 */
public class IsComposingUtil {

    private static final String TAG = "IsComposingUtil";
    private static final String encoding = "UTF-8";
    private static final String namespace = "urn:ietf:params:xml:ns:im-iscomposing";

    private static enum ParsingState {
        init,
        readingRoot,
        readingState,
        readingRefresh,
        readingContentType
    }


    public static IsComposing fromXml(byte[] serializedXml) {
        try {

            String resultState = null;
            String resultMimeType = null;
            String resultRefresh = null;

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(serializedXml), encoding);
            ParsingState parsingState = ParsingState.init;
            int eventType = parser.getEventType();

            int unknownTagLevelKeeper = 0;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        if (unknownTagLevelKeeper > 0) {
                            ++unknownTagLevelKeeper;
                        } else {
                            switch (parsingState) {
                                case init:
                                    if (namespace.equals(parser.getNamespace()) && "isComposing".equals(parser.getName())) {
                                        parsingState = ParsingState.readingRoot;
                                    } else {
                                        throw new IOException("Unexpected root tag " + parser.getName());
                                    }
                                    break;
                                case readingRoot:
                                    if (!namespace.equals(parser.getNamespace())) {
                                        throw new IOException("Unexpected namespace");
                                    }
                                    if ("state".equals(parser.getName())) {
                                        parsingState = ParsingState.readingState;
                                    } else if ("refresh".equals(parser.getName())) {
                                        parsingState = ParsingState.readingRefresh;
                                    } else if ("contenttype".equals(parser.getName())) {
                                        parsingState = ParsingState.readingContentType;
                                    } else {
                                        unknownTagLevelKeeper = 1;
                                    }
                                    break;
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (unknownTagLevelKeeper > 0) {
                            --unknownTagLevelKeeper;
                        } else {
                            switch (parsingState) {
                                case readingContentType:
                                case readingState:
                                case readingRefresh:
                                    parsingState = ParsingState.readingRoot;
                                    break;
                                case readingRoot:
                                    // we should be finished now
                                    break;
                            }
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (unknownTagLevelKeeper == 0) {
                            switch (parsingState) {
                                case readingState:
                                    resultState = parser.getText();
                                    break;
                                case readingContentType:
                                    resultMimeType = parser.getText();
                                    break;
                                case readingRefresh:
                                    resultRefresh = parser.getText();
                                    break;
                            }
                        }
                        break;
                    default:
                        throw new RuntimeException("Unimplemented event");
                }
                eventType = parser.next();
            }

            return new IsComposing(
                    resultRefresh == null ? -1 : Integer.parseInt(resultRefresh),
                    resultState.equals(IsComposing.State.active.toString()) ? IsComposing.State.active : IsComposing.State.idle,
                    resultMimeType == null ? null : MimeType.parse(resultMimeType)
            );

        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }

    public static byte[] toXml(IsComposing isComposing) {
        ChangedKXmlSerializer serializer = new ChangedKXmlSerializer();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            serializer.setOutput(os, encoding);
            serializer.startDocument(encoding, null);
            serializer.setPrefix("", namespace);
            serializer.startTag(namespace, "isComposing");
            serializer.startTag(null, "state");
            serializer.text(isComposing.getState().toString());
            serializer.endTag(null, "state");
            if (isComposing.getContentType() != null) {
                serializer.startTag(null, "contenttype");
                serializer.text(isComposing.getContentType().toString());
                serializer.endTag(null, "contenttype");
            }
            if (isComposing.getRefresh() > -1) {
                serializer.startTag(null, "refresh");
                serializer.text(String.valueOf(isComposing.getRefresh()));
                serializer.endTag(null, "refresh");
            }

            serializer.endTag(namespace, "isComposing");
            serializer.endDocument();
            serializer.flush();
            return os.toByteArray();
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }
}
