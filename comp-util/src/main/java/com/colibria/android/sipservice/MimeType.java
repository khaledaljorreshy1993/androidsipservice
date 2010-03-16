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

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;

public class MimeType implements Serializable, Cloneable {

    private static final String TAG = "MimeType";

    /*
     * List of well known MIME types:
     */
    public static final MimeType TEXT_HTML = safeConstruct("text/html");
    public static final MimeType APPLICATION_POSTSCRIPT = safeConstruct("application/postscript");
    public static final MimeType TEXT_PLAIN = safeConstruct("text/plain");
    public static final MimeType APPLICATION_X_WWW_FORM_URLENCODED = safeConstruct("application/x-www-form-urlencoded");
    public static final MimeType APPLICATION_OCTET_STREAM = safeConstruct("application/octet-stream");
    public static final MimeType APPLICATION_RESOURCE_LISTS_XML = safeConstruct("application/resource-lists+xml");
    public static final MimeType APPLICATION_RLS_SERVICES_XML = safeConstruct("application/rls-services+xml");
    public static final MimeType APPLICATION_WATCHERINFO_XML = safeConstruct("application/watcherinfo+xml");
    public static final MimeType APPLICATION_PIDF_XML = safeConstruct("application/pidf+xml");
    public static final MimeType APPLICATION_PIDF_DIFF_XML = safeConstruct("application/pidf-diff+xml");
    public static final MimeType APPLICATION_CONFERENCE_INFO_XML = safeConstruct("application/conference-info+xml");
    public static final MimeType APPLICATION_POC_SETTINGS_XML = safeConstruct("application/poc-settings+xml");
    public static final MimeType APPLICATION_REG_INFO_XML = safeConstruct("application/reginfo+xml");
    public static final MimeType APPLICATION_RLMI_XML = safeConstruct("application/rlmi+xml");
    public static final MimeType APPLICATION_AUTH_POLICY_XML = safeConstruct("application/auth-policy+xml");
    public static final MimeType APPLICATION_SDP = safeConstruct("application/sdp");
    public static final MimeType APPLICATION_IM_ISCOMPOSING_XML = safeConstruct("application/im-iscomposing+xml");
    public static final MimeType APPLICATION_FILTER_XML = safeConstruct("application/simple-filter+xml");
    public static final MimeType APPLICATION_DEFERRED_LIST = safeConstruct("application/vnd.oma.im.deferred-list+xml");
    public static final MimeType APPLICATION_VND_OMA_SEARCH = safeConstruct("application/vnd.oma.search+xml");
    public static final MimeType APPLICATION_XCAP_EL = safeConstruct("application/xcap-el+xml");
    public static final MimeType APPLICATION_XCAP_DIFF_XML = safeConstruct("application/xcap-diff+xml");
    public static final MimeType APPLICATION_LIST_SERVICE_XML = safeConstruct("application/list-service+xml");
    public static final MimeType APPLICATION_POC_GROUPS = safeConstruct("application/vnd.oma.poc.groups+xml");
    public static final MimeType APPLICATION_IM_DEFERRED_LIST = safeConstruct("application/vnd.oma.im.deferred-list+xml");
    public static final MimeType APPLICATION_IM_HISTORY_LIST = safeConstruct("application/vnd.oma.im.history-list+xml");
    public static final MimeType APPLICATION_VND_OMA_PRES_CONTENT_XML = safeConstruct("application/vnd.oma.pres-content+xml");
    public static final MimeType APPLICATION_VND_OMA_USER_PROFILE_XML = safeConstruct("application/vnd.oma.user-profile+xml");
    public static final MimeType APPLICATION_VND_OMA_GROUP_USAGE_LIST_XML = safeConstruct("application/vnd.oma.group-usage-list+xml");
    public static final MimeType MULTIPART_FORM_DATA = safeConstruct("multipart/form-data");
    public static final MimeType MULTIPART_MIXED = safeConstruct("multipart/mixed");
    public static final MimeType MULTIPART_RELATED = safeConstruct("multipart/related");
    public static final MimeType APPLICATION_X_JAVA_AGENT = safeConstruct("application/x-java-agent");
    public static final MimeType MESSAGE_HTTP = safeConstruct("message/http");
    public static final MimeType MESSAGE_CPIM = safeConstruct("message/CPIM");
    public static final MimeType MESSAGE_EXTERNAL_BODY = safeConstruct("message/external-body");
    public static final MimeType MESSAGE_SIPFRAG = safeConstruct("message/sipfrag;version=2.0");
    public static final MimeType TEXT_CSS = safeConstruct("text/css");
    public static final MimeType TEXT_XML = safeConstruct("text/xml");
    public static final MimeType TEXT = safeConstruct("text/*");
    public static final MimeType APPLICATION_RDF_XML = safeConstruct("application/rdf+xml");
    public static final MimeType APPLICATION_XHTML_XML = safeConstruct("application/xhtml+xml");
    public static final MimeType APPLICATION_XML = safeConstruct("application/xml");
    public static final MimeType IMAGE_JPEG = safeConstruct("image/jpeg");
    public static final MimeType IMAGE_GIF = safeConstruct("image/gif");
    public static final MimeType IMAGE_PNG = safeConstruct("image/png");
    public static final MimeType DEFAULT_MIME = safeConstruct("text/plain;charset=utf-8");
    public static final MimeType EMPTY = safeConstruct("empty/empty");


    public static final int NO_MATCH = -1;
    public static final int MATCH_TYPE = 1;
    public static final int MATCH_SPECIFIC_TYPE = 2;
    public static final int MATCH_SUBTYPE = 3;
    public static final int MATCH_SPECIFIC_SUBTYPE = 4;

    public static final String START = "*".intern();
    public static final String text = "text".intern();

    private static MimeType safeConstruct(String spec) {
        try {
            return parse(spec);
        } catch (IOException e) {
            Logger.e(TAG, "Bad programmer!", e);
            return null;
        }
    }

    /**
     * Constructs a new MimeType instance based on the provided string.
     *
     * @param spec the string which is to be parsed
     * @return a new instance of MimeType
     * @throws IOException in case of an parse error
     */
    public static MimeType parse(String spec) throws IOException {

        String type, subType;
        Map<String, String> paramsMap = new HashMap<String, String>();

        if (spec == null)
            spec = DEFAULT_MIME.toString();

        spec = spec.trim();

        String tokens[] = spec.split("/");

        // Get type
        if (tokens.length != 2) {
            throw new IOException("No '/' delimiter: " + spec);
        }
        type = tokens[0].trim().intern();

        String[] parameters = tokens[1].split(";");
        if (parameters.length == 1) {
            subType = tokens[1].trim().intern();
        } else {
            subType = parameters[0].trim().intern();

            for (int parIndex = 1; parIndex < parameters.length; parIndex++) {
                // Take into account that the a parameter might be 'boundary="----=_Part_0_15907981.1226531888597"'
                String keyValue = parameters[parIndex];
                int split = keyValue.indexOf("=");
                if (split > 0) {
                    String key = keyValue.substring(0, split).trim();
                    String value = keyValue.substring(split + 1).trim();
                    paramsMap.put(key, value);
                } else {
                    throw new IOException("Parameter must be key=value: " + spec);
                }
            }
        }

        return new MimeType(type, subType, paramsMap);
    }

    /**
     * Constructs a new instance of MimeType
     *
     * @param type       the type
     * @param subType    the subType
     * @param parameters a list of parameters which should be included where every second string represents a value.
     * @return a new instance of MimeType
     * @throws java.io.IOException in case of invalid input
     */
    public static MimeType create(String type, String subType, Collection<NameValuePair> parameters) throws IOException {
        if (type == null) {
            throw new IOException("Type cannot be null");
        }
        if (subType == null) {
            throw new IOException("Subtype cannot be null");
        }

        HashMap<String, String> parametersMap = new HashMap<String, String>();
        if (parameters != null)
            for (NameValuePair nvp : parameters) {
                parametersMap.put(nvp.getName(), nvp.getValue());
            }

        return new MimeType(type, subType, parametersMap);
    }

    /*
     * This is actually an immutable object. However, to minimize the
     * number of bytes required to serialize this object, we serialize
     * only the field serializedTemp, which contains the string
     * representation of this object.
     */
    private volatile transient String type;
    private volatile transient String subtype;
    private volatile transient Map<String, String> parameters;
    private volatile transient String toStringCache;
    private String serializedTemp;

    private MimeType(String type, String subType, Map<String, String> params) {
        this.type = type;
        this.subtype = subType;
        this.parameters = Collections.unmodifiableMap(params);
    }

    /**
     * How good the given MimeType matches the receiver of the method ?
     * This method returns a matching level among:
     * <dl>
     * <dt>NO_MATCH<dd>Types not matching,</dd>
     * <dt>MATCH_TYPE<dd>Types match,</dd>
     * <dt>MATCH_SPECIFIC_TYPE<dd>Types match exactly,</dd>
     * <dt>MATCH_SUBTYPE<dd>Types match, subtypes matches too</dd>
     * <dt>MATCH_SPECIFIC_SUBTYPE<dd>Types match, subtypes matches exactly</dd>
     * </dl>
     * The matches are ranked from worst match to best match, a simple
     * Max ( match[i], matched) will give the best match.
     *
     * @param other The other MimeType to match against ourself.
     * @return best match detected
     */
    public int match(MimeType other) {
        int match = NO_MATCH;

        /*
        * match types
        */
        if (type == null) {
            return match;
        } else {
            if ((START.equals(type)) || (START.equals(other.type))) {
                return MATCH_TYPE;
            } else if (!type.equalsIgnoreCase(other.type)) {
                return NO_MATCH;
            } else {
                match = MATCH_SPECIFIC_TYPE;
            }
        }

        /*
         * match subtypes
         */
        if (subtype == null) {
            return match;
        } else {
            if ((START.equals(subtype)) || (START.equals(other.subtype))) {
                match = MATCH_SUBTYPE;
            } else if (!subtype.equalsIgnoreCase(other.subtype)) {
            } else {
                match = MATCH_SPECIFIC_SUBTYPE;
            }
        }
        return match;
    }

    /**
     * test if recipient (mime) is accepting this mime.
     * LHS is sender, RHS is recipient
     *
     * @param recipient mime type
     * @return true if accepted
     */
    public boolean acceptedBy(MimeType recipient) {
        if (recipient == null)
            return false;
        int res = this.match(recipient);

        // wildcards no accepted on sender
        if (START.equals(type) || START.equals(subtype))
            return false;

        switch (res) {
            case NO_MATCH:
            case MATCH_TYPE:
            case MATCH_SPECIFIC_TYPE:
                return false;
            case MATCH_SUBTYPE:
            case MATCH_SPECIFIC_SUBTYPE:
                // is this ok?.. should be :-o

                if (!"text".equals(this.type)) {
                    return true;
                } else {
                    if (recipient.getParameterValue("charset") == null)
                        return false;

                    Charset csSender;
                    Charset csReceiver;
                    try {
                        csSender = Charset.forName(this.getParameterValue("charset"));
                        csReceiver = Charset.forName(recipient.getParameterValue("charset"));
                    } catch (UnsupportedCharsetException e) {
                        Logger.e(TAG, "supplied characterset is not recognized:" +
                                this.getParameterValue("charset") +
                                " or " + recipient.getParameterValue("charset"));
                        return false;
                    }
                    return csReceiver.contains(csSender);
                }
        }

        return false;
    }

    /**
     * Find out if mime types are equivalent, based on heuristics
     * like text/xml <=> application/xml and other problems related
     * to format that may have multiple mime types.
     * Note that text/html and application/xhtml+xml are not exactly
     * the same
     *
     * @param mtype a MimeType
     * @return a boolean, true if the two mime types are equivalent
     * @deprecated use equals or match with MATCH_SPECIFIC_SUBTYPE instead
     */
    public boolean equiv(MimeType mtype) {
        if (match(mtype) == MATCH_SPECIFIC_SUBTYPE) {
            return true;
        }
        if ((match(TEXT_XML) == MATCH_SPECIFIC_SUBTYPE) ||
                (match(APPLICATION_XML) == MATCH_SPECIFIC_SUBTYPE)) {
            if ((mtype.match(TEXT_XML) == MATCH_SPECIFIC_SUBTYPE) ||
                    (mtype.match(APPLICATION_XML) == MATCH_SPECIFIC_SUBTYPE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does this MIME type has some value for the given parameter ?
     *
     * @param name The parameter to check.
     * @return <strong>True</strong> if this parameter has a value, false
     *         otherwise.
     */
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }

    /**
     * Get the major type of this mime type.
     *
     * @return The major type, encoded as a String.
     */

    public String getType() {
        return type;
    }

    /**
     * Get the minor type (subtype) of this mime type.
     *
     * @return The minor or subtype encoded as a String.
     */
    public String getSubtype() {
        return subtype;
    }

    /**
     * Get a mime type parameter value.
     *
     * @param name The parameter whose value is to be returned.
     * @return The parameter value, or <b>null</b> if not found.
     */
    public String getParameterValue(String name) {
        return parameters.get(name);
    }

    /**
     * Get all named parameters associated with this Mime
     *
     * @return parameter names
     */
    public Set<String> getParameterNames() {
        return parameters.keySet();
    }

    /**
     * Check if the charset spec (if any) in this mime is a supported character set
     *
     * @return true if ok, or not present
     */
    public boolean supportedCharset() {
        if (text.equals(type)) {
            try {
                Charset.forName(this.getParameterValue("charset"));
            } catch (UnsupportedCharsetException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if this MIME Type is in the accept header
     *
     * @param acceptedMimeTypes from the accept header
     * @return true if this MIME Type is accepted
     */
    public boolean acceptedByHeader(List<MimeType> acceptedMimeTypes) {
        for (MimeType accepts : acceptedMimeTypes) {
            if (this.acceptedBy(accepts)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other instanceof MimeType && this.match((MimeType) other) == MATCH_SPECIFIC_SUBTYPE;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "CloneDoesntCallSuperClone"})
    @Override
    public MimeType clone() {
        HashMap<String, String> copy = new HashMap<String, String>();
        for (String key : parameters.keySet()) {
            copy.put(key, parameters.get(key));
        }
        return new MimeType(type, subtype, copy);
    }

    @Override
    public String toString() {
        if (toStringCache == null) {
            StringBuffer sb = new StringBuffer(type);
            sb.append('/');
            sb.append(subtype);
            if (parameters != null) {
                String value;
                for (String key : parameters.keySet()) {
                    sb.append(';');
                    sb.append(key);
                    if ((value = parameters.get(key)) != null) {
                        sb.append("=");
                        if (!value.startsWith("\"")) {
                            sb.append('"').append(value).append('"');
                        } else {
                            sb.append(value);
                        }
                    }
                }
            }
            toStringCache = sb.toString().intern();
        }
        return toStringCache;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        serializedTemp = toString();
        out.defaultWriteObject();
        serializedTemp = null;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        MimeType mt = MimeType.parse(serializedTemp);
        this.type = mt.type;
        this.subtype = mt.subtype;
        this.parameters = mt.parameters;
        serializedTemp = null;
    }
}
