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
package com.colibria.android.sipservice.sdp.api;

/**
 * This interface defines the allowed SDP attributes
 *
 * @author bakke
 */
public interface SdpAttribute {

    public static final String ACCEPT_TYPES = "accept-types";

    public static final String ACCEPT_WRAPPED_TYPES = "accept-wrapped-types";

    public static final String PATH = "path";

    public static final String SETUP = "setup";

    public static final String SENDRECV = "sendrecv";

    public static final String SENDONLY = "sendonly";

    public static final String RECVONLY = "recvonly";

    public static final String FILESELECTOR = "file-selector";

    public static final String FILE_DISPOSITION = "file-disposition";

    public static final String FILE_TRANSFER_ID = "file-transfer-id";

    public static final String CONNECTION = "connection";

    public static final String SUCCESS_REPORT = "Success-Report";

    public static final String FAILURE_REPORT = "Failure-Report";
}
