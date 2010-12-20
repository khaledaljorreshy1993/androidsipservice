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
package com.colibria.android.sipservice.xml.resourcelists;

import com.colibria.android.sipservice.logging.Logger;
import com.colibria.android.sipservice.xml.ChangedKXmlSerializer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * @author Sebastian Dehne
 */
public class ResourceListsUtil {
    private static final String TAG = "ResourceListsUtil";
    private static final String encoding = "UTF-8";
    private static final String namespace = "urn:ietf:params:xml:ns:resource-lists";

    private static enum ParsingState {
        init,
        readingRoot,
        readingSublistInit,
        readingSublistCont,
        readingEntry,
        readingEntryRef,
        readingExternal
    }

    public static ResourceLists fromXml(byte[] serializedXml) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(serializedXml), encoding);
            int eventType = parser.getEventType();
            ResourceLists resourceLists = new ResourceLists(new LinkedList<AbstractEntry>());

            ParsingState parsingState = ParsingState.init;
            boolean readingDisplayName = false;

            String temp = null, displayName = null;
            Stack<List<AbstractEntry>> stack = new Stack<List<AbstractEntry>>();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        switch (parsingState) {
                            case init:
                                if (namespace.equals(parser.getNamespace()) && "resource-lists".equals(parser.getName())) {
                                    parsingState = ParsingState.readingRoot;
                                    stack.push(resourceLists.getLists());
                                } else {
                                    throw new IOException("Unexpected root tag " + parser.getName());
                                }
                                break;
                            case readingRoot:
                                if (!namespace.equals(parser.getNamespace())) {
                                    throw new IOException("Unexpected namespace");
                                }
                                if ("list".equals(parser.getName())) {
                                    temp = parser.getAttributeValue("", "name");
                                    parsingState = ParsingState.readingSublistInit;
                                } else {
                                    throw new IOException("Unexpected tag " + parser.getName());
                                }
                                break;
                            case readingSublistInit:
                                if (!namespace.equals(parser.getNamespace())) {
                                    throw new IOException("Unexpected namespace");
                                }
                                parsingState = ParsingState.readingSublistCont;
                                if ("display-name".equals(parser.getName())) {
                                    readingDisplayName = true;
                                    break;
                                } else {
                                    ResourceList tmp = new ResourceList(new LinkedList<AbstractEntry>(), null, temp);
                                    stack.peek().add(tmp);
                                    stack.push(tmp.getLists());
                                }
                                // note: fall through here!
                            case readingSublistCont:
                                if (!namespace.equals(parser.getNamespace())) {
                                    throw new IOException("Unexpected namespace");
                                }
                                displayName = null;
                                if ("entry".equals(parser.getName())) {
                                    parsingState = ParsingState.readingEntry;
                                    temp = parser.getAttributeValue("", "uri");
                                } else if ("external".equals(parser.getName())) {
                                    parsingState = ParsingState.readingExternal;
                                    temp = parser.getAttributeValue("", "anchor");
                                } else if ("entry-ref".equals(parser.getName())) {
                                    parsingState = ParsingState.readingEntryRef;
                                } else if ("list".equals(parser.getName())) {
                                    temp = parser.getAttributeValue("", "name");
                                    parsingState = ParsingState.readingSublistInit;
                                }
                                break;
                            case readingExternal:
                            case readingEntry:
                                // this can only be the display name
                                if (!namespace.equals(parser.getNamespace())) {
                                    throw new IOException("Unexpected namespace");
                                }
                                if ("display-name".equals(parser.getName())) {
                                    readingDisplayName = true;
                                } else {
                                    throw new IOException("Unexpected tag " + parser.getName());
                                }
                                break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        switch (parsingState) {
                            case readingEntryRef:
                                parsingState = ParsingState.readingSublistCont;
                                break;
                            case readingEntry:
                                if (readingDisplayName) {
                                    readingDisplayName = false;
                                    break;
                                }
                                stack.peek().add(new Entry(temp, displayName));
                                parsingState = ParsingState.readingSublistCont;
                                break;
                            case readingExternal:
                                if (readingDisplayName) {
                                    readingDisplayName = false;
                                    break;
                                }
                                stack.peek().add(new External(displayName, temp));
                                parsingState = ParsingState.readingSublistCont;
                                break;
                            case readingSublistInit:
                                if (readingDisplayName) {
                                    readingDisplayName = false;
                                    ResourceList tmp = new ResourceList(new LinkedList<AbstractEntry>(), displayName, temp);
                                    stack.peek().add(tmp);
                                } else {
                                    ResourceList tmp = new ResourceList(new LinkedList<AbstractEntry>(), null, temp);
                                    stack.peek().add(tmp);
                                }
                                if (stack.size() == 1) {
                                    parsingState = ParsingState.readingRoot;
                                } else {
                                    parsingState = ParsingState.readingSublistCont;
                                }
                                break;
                            case readingSublistCont:
                                if (readingDisplayName) {
                                    readingDisplayName = false;
                                    ResourceList tmp = new ResourceList(new LinkedList<AbstractEntry>(), displayName, temp);
                                    stack.peek().add(tmp);
                                    stack.push(tmp.getLists());
                                    break;
                                }
                                stack.pop();
                                if (stack.size() == 1) {
                                    parsingState = ParsingState.readingRoot;
                                } else {
                                    parsingState = ParsingState.readingSublistCont;
                                }
                                break;
                            case readingRoot:
                                // we should be finished now
                                break;
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (readingDisplayName)
                            displayName = parser.getText();
                        break;
                    default:
                        throw new RuntimeException("Unimplemented event");
                }
                eventType = parser.next();
            }
            return resourceLists;
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }

    public static byte[] toXml(ResourceLists list) {
        ChangedKXmlSerializer serializer = new ChangedKXmlSerializer();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            serializer.setOutput(os, encoding);
            serializer.startDocument(encoding, null);
            serializer.setPrefix("", namespace);
            serializer.startTag(namespace, "resource-lists");
            for (AbstractEntry ae : list.getLists()) {
                if (ae instanceof Entry) {
                    Entry e = (Entry) ae;
                    serializer.startTag(null, "entry");
                    serializer.attribute(null, "uri", e.getUri());
                    if (e.getDisplayName() != null) {
                        serializer.startTag(null, "display-name");
                        serializer.text(e.getDisplayName());
                        serializer.endTag(null, "display-name");
                    }
                    serializer.endTag(null, "entry");
                } else if (ae instanceof External) {
                    External ex = (External) ae;
                    serializer.startTag(null, "external");
                    serializer.attribute(null, "anchor", ex.getAnchor());
                    if (ex.getDisplayName() != null) {
                        serializer.startTag(null, "display-name");
                        serializer.text(ex.getDisplayName());
                        serializer.endTag(null, "display-name");
                    }
                    serializer.endTag(null, "external");
                } else if (ae instanceof ResourceList) {
                    writeSublist(serializer, (ResourceList) ae);
                }
            }
            serializer.endTag(namespace, "resource-lists");
            serializer.endDocument();
            serializer.flush();
            return os.toByteArray();
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return null;
    }

    private static void writeSublist(XmlSerializer serializer, ResourceList sublist) throws IOException {
        serializer.startTag(null, "list");
        if (sublist.getDisplayName() != null) {
            serializer.attribute(null, "name", sublist.getDisplayName());
        }
        for (AbstractEntry ae : sublist.getLists()) {
            if (ae instanceof Entry) {
                Entry e = (Entry) ae;
                serializer.startTag(null, "entry");
                serializer.attribute(null, "uri", e.getUri());
                if (e.getDisplayName() != null) {
                    serializer.startTag(null, "display-name");
                    serializer.text(e.getDisplayName());
                    serializer.endTag(null, "display-name");
                }
                serializer.endTag(null, "entry");
            } else if (ae instanceof External) {
                External ex = (External) ae;
                serializer.startTag(null, "external");
                serializer.attribute(null, "anchor", ex.getAnchor());
                if (ex.getDisplayName() != null) {
                    serializer.startTag(null, "display-name");
                    serializer.text(ex.getDisplayName());
                    serializer.endTag(null, "display-name");
                }
                serializer.endTag(null, "external");
            } else if (ae instanceof ResourceList) {
                writeSublist(serializer, (ResourceList) ae);
            }
        }
        serializer.endTag(null, "list");
    }

}
