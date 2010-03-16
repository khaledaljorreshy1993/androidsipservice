/*
* Conditions Of Use 
* 
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
* 
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
* 
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement
*  
* .
* 
*/
/*
 * Version.java
 *
 * Created on January 9, 2002, 11:29 AM
 */

package com.colibria.android.sipservice.sdp.api;

/**
 * A Version field represents the v= fields contained within the SessionDescription.
 * <p/>
 * Please refer to IETF RFC 2327 for a description of SDP.
 *
 * @author deruelle
 * @version 1.0
 */
public interface Version extends Field {


    /**
     * Returns the version number.
     *
     * @return int
     * @throws SdpParseException
     */
    public int getVersion()
            throws SdpParseException;

    /**
     * Sets the version.
     *
     * @param value the - new version value.
     * @throws SdpException if the value is <=0
     */
    public void setVersion(int value)
            throws SdpException;
}

