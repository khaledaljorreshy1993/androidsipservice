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
import com.colibria.android.sipservice.sip.SipStackTestBase;
import com.colibria.android.sipservice.sip.ISipStackListener;
import com.colibria.android.sipservice.sip.messages.Request;
import com.colibria.android.sipservice.sip.messages.SipMessage;
import com.colibria.android.sipservice.sip.messages.Message;
import com.colibria.android.sipservice.sip.parser.SipMessageParser;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Sebastian Dehne
 */
public class SipMessageParseTest extends SipStackTestBase {

    private static final String TAG = "SipMessageParseTest";
    private volatile ServerSocket ss;
    private volatile Socket client;
    private final LinkedBlockingQueue<Request> receivedRequests = new LinkedBlockingQueue<Request>();

    @Override
    protected void setUp() throws Exception {
        listener = new ISipStackListener() {
            @Override
            public void processRequest(Request r) {
                receivedRequests.offer(r);
            }
        };
        dstHst = "localhost";
        dstPort = 8001;

        ss = new ServerSocket(dstPort, 1, InetAddress.getByName(dstHst));
        super.setUp();
        client = ss.accept();
        Logger.i(TAG, "Client connected");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        client.close();
        ss.close();
    }

    public void testSipParser() throws Exception {
        byte[] responseMsg = ("SIP/2.0 180 This is a ringing message\r\n" +
                "From: sip:bob@example.com;tag=1234\r\n" +
                "To: Alice <sip:alice@example.com>;tag=1234\r\n" +
                "Call-ID: kjasldj1o2iulaska@alksjdla.com\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Max-Forwards: 70\r\n" +
                "Content-Type: application/sdp;name=value\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n").getBytes();
        byte[] responseMsg2 = ("REGISTER sip:alice@example.com SIP/2.0\r\n" +
                "From: sip:bob@example.com;tag=1234\r\n" +
                "To: Alice <sip:alice@example.com>;tag=1234\r\n" +
                "Call-ID: kjasldj1o2iulaska@alksjdla.com\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Via: SIP/2.0/TCP myname, SIP/2.0/TCP myname2:5061;branch=1234;received=1.2.3.4\r\n" +
                "Max-Forwards: 70\r\n" +
                "Event: presence.info ;id=1234\r\n" +
                "Event: presence.info ;id=1234\r\n" +
                "Content-Type: application/sdp;name=value\r\n" +
                "Contact: <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Contact: <sip:alice@bob.com:1234>;sometag1;sometag2=value, <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Contact: \"Alice in Wonderland \" <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Content-Length: 1\r\n" +
                "\r\n" +
                "1").getBytes();
        byte[] messageRequest = ("MESSAGE sip:alice@10.0.2.15;transport=tcp SIP/2.0\r\n" +
                "Call-ID: 8522-1@sipserver\r\n" +
                "CSeq: 1000 MESSAGE\r\n" +
                "From: <sip:bob@colibria.com>;tag=501\r\n" +
                "To: <sip:alice@colibria.com>\r\n" +
                "Max-Forwards: 65\r\n" +
                "Subscription-State: waiting\r\n" +
                "Subscription-State: terminated;expires=60;reason=deactivated;retry-after=120\r\n" +
                "Supported: timer\r\n" +
                "Accept: text/plain\r\n" +
                "Accept: image/gif\r\n" +
                "Accept: image/png, image/jpeg\r\n" +
                "Subscription-State: active;expires=60\r\n" +
                "Subject: This is the MESSAGE subject\r\n" +
                "P-Asserted-Identity: <sip:bob@colibria.com>\r\n" +
                "P-Access-Network-Info: 3GPP-UTRAN-TDD; utran-cell-id-3gpp=234151D0FCE11\r\n" +
                "User-Agent: IM-client/OMA1.0 Colibria-PerlClient/v1.01\r\n" +
                "Accept-Contact: *;+g.oma.sip-im\r\n" +
                "Via: SIP/2.0/TCP 127.0.0.1:5060;branch=z9hG4bK07921d79ef0522c95d99f487f030cfbf\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "hallo").getBytes();
        byte[] requestMsg = ("NOTIFY sip:dehne1@cb69ad21570a51f6.192.168.1.183;transport=tcp SIP/2.0\r\n" +
                "Call-ID: fbb229dc84e9fad35bc777fa9c39868f@colibria.com\r\n" +
                "CSeq: 2 NOTIFY\r\n" +
                "From: \"Alice in Wonderland\" <sip:dehne1@colibria.com>;tag=3349\r\n" +
                "To: \"Alice in Wonderland\" <sip:dehne1@colibria.com>;tag=7400\r\n" +
                "Max-Forwards: 70\r\n" +
                "Route: <sip:95.130.218.67;lr;transport=tcp>\r\n" +
                "Contact: <sip:dehne1@95.130.218.67:5061;transport=tcp>\r\n" +
                "Event: presence\r\n" +
                "P-Charging-Vector: icid-value=AS-95.130.218.67-1265119824328\r\n" +
                "Subscription-State: active;expires=14399;min-interval=0\r\n" +
                "Expires: 14399\r\n" +
                "Via: SIP/2.0/TCP 95.130.218.67:5061;alias;branch=z9hG4bKf3c41c402625282014df9db56d143bcb\r\n" +
                "Content-Type: multipart/related;boundary=\"----=_Part_5259_780966552.1265199262594\";start=\"<1265199262594-1101662577@colibria.com>\";type=\"application/rlmi+xml\"\r\n"+
                "Content-Type: application/pidf+xml\r\n" +
                "Content-Length: 307\r\n" +
                "\r\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><presence xmlns=\"urn:ietf:params:xml:ns:pidf\" entity=\"sip:dehne1@colibria.com\">\n" +
                "    <tuple id=\"xx998877yy\">\n" +
                "        <status>\n" +
                "            <basic>closed</basic>\n" +
                "        </status>\n" +
                "        <timestamp>2010-02-02T14:10:24Z</timestamp>\n" +
                "    </tuple>\n" +
                "</presence>").getBytes();

        SipMessageParser sipMessageParser = new SipMessageParser();
        ByteBuffer bb = ByteBuffer.allocate(1024 * 8);
        SipMessage result;

        int count = 200000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            bb.flip();
            readMoreBytes(bb, requestMsg, requestMsg.length);
            sipMessageParser.reset();
            SipMessage m;
            if ((m = sipMessageParser.parseMoreBytes(bb)) == null) {
                throw new IllegalArgumentException();
            }
            bb.clear();
        }
        long timeSpent = (System.currentTimeMillis() - start) / 1000;
        int performance = (int) (count / timeSpent);
        System.out.println("done : " + timeSpent + " - " + performance);
    }

    public void testSipParserWithRecordRoute() throws Exception {
        byte[] responseMsg = ("SIP/2.0 200 OK\r\n" +
                "Call-ID: 215e4983fa4ab92ba10fc8c8302483fd@colibria.com\r\n" +
                "From: \"android1\" <sip:android1@colibria.com>;tag=518\r\n" +
                "To: \"android1\" <sip:android1@colibria.com>;tag=9105\r\n" +
                "CSeq: 3 REGISTER\r\n" +
                "Via: SIP/2.0/TCP 10.0.2.15:5060;alias;branch=z9hG4bKa6489b46495c470679507a2dd9333a6c;received=192.168.10.36;rport=48951\r\n" +
                "Contact: <sip:android1@10.0.2.15;transport=tcp>;expires=3599;q=1.0,<sip:android1@192.168.10.71:5050;transport=udp>;expires=844;q=1.0\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n").getBytes();

        semaphore.acquire();
        int bytesSent = 0;
        client.getOutputStream().write(responseMsg, bytesSent, responseMsg.length);
        client.getOutputStream().flush();
        bytesSent += responseMsg.length;
        Thread.sleep(300);
        semaphore.release();
        Thread.sleep(300);

    }

    public void testSipParserWithTcp() throws Exception {
        byte[] responseMsg2 = ("REGISTER sip:alice@example.com SIP/2.0\r\n" +
                "From: sip:bob@example.com;tag=1234\r\n" +
                "To: Alice <sip:alice@example.com>;tag=1234\r\n" +
                "Call-ID: kjasldj1o2iulaska@alksjdla.com\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Via: SIP/2.0/TCP myname;branch=z9hG4bK1234, SIP/2.0/TCP myname2:5061;branch=1234;received=1.2.3.4\r\n" +
                "Max-Forwards: 70\r\n" +
                "Event: presence.info ;id=1234\r\n" +
                "Content-Type: application/sdp;name=value\r\n" +
                "Contact: <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Contact: <sip:alice@bob.com:1234>;sometag1;sometag2=value, <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Contact: \"Alice in Wonderland \" <sip:alice@bob.com:1234>;sometag1;sometag2=value\r\n" +
                "Content-Length: 1\r\n" +
                "\r\n" +
                "1").getBytes();
        byte[] doubleMessageRequest = ("MESSAGE sip:alice@10.0.2.15;transport=tcp SIP/2.0\r\n" +
                "Call-ID: 8522-1@sipserver\r\n" +
                "CSeq: 1000 MESSAGE\r\n" +
                "From: <sip:bob@colibria.com>;tag=501\r\n" +
                "To: <sip:alice@colibria.com>\r\n" +
                "Max-Forwards: 65\r\n" +
                "Supported: timer\r\n" +
                "Subject: This is the MESSAGE subject\r\n" +
                "P-Asserted-Identity: <sip:bob@colibria.com>\r\n" +
                "P-Access-Network-Info: 3GPP-UTRAN-TDD; utran-cell-id-3gpp=234151D0FCE11\r\n" +
                "User-Agent: IM-client/OMA1.0 Colibria-PerlClient/v1.01\r\n" +
                "Accept-Contact: *;+g.oma.sip-im\r\n" +
                "Via: SIP/2.0/TCP 127.0.0.1:5060;branch=z9hG4bK07921d79ef0522c95d99f487f030cfbf,SIP/2.0/UDP 127.0.0.1:5071;branch=z9hG4bKfbc592069eaa3505fe7315dd0c79c921,SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK7bf2b3b9f56af05fbdd48a6b0597503e,SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bKf07ef432529eb48dfa41c8033d15b21a,SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK90db90a49a7de047827bb0d1fcef2591,SIP/2.0/UDP sipserver:5050;branch=z9hG4bK-a1c3af132cd3579aff58b10b17402e11;received=127.0.0.1\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "hallo" +
                "\n" + // <- this is a keep-alive
                "MESSAGE sip:alice@10.0.2.15;transport=tcp SIP/2.0\r\n" +
                "Call-ID: 8522-1@sipserver\r\n" +
                "CSeq: 1000 MESSAGE\r\n" +
                "From: <sip:bob@colibria.com>;tag=501\r\n" +
                "To: <sip:alice@colibria.com>\r\n" +
                "Max-Forwards: 65\r\n" +
                "Supported: timer\r\n" +
                "Subject: This is the MESSAGE subject\r\n" +
                "P-Asserted-Identity: <sip:bob@colibria.com>\r\n" +
                "P-Access-Network-Info: 3GPP-UTRAN-TDD; utran-cell-id-3gpp=234151D0FCE11\r\n" +
                "User-Agent: IM-client/OMA1.0 Colibria-PerlClient/v1.01\r\n" +
                "Accept-Contact: *;+g.oma.sip-im\r\n" +
                "Via: SIP/2.0/TCP 127.0.0.1:5060;branch=z9hG4bK07921d79ef05212c95d99f487f030cfbf,SIP/2.0/UDP 127.0.0.1:5071;branch=z9hG4bKfbc592069eaa3505fe7315dd0c79c921,SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK7bf2b3b9f56af05fbdd48a6b0597503e,SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bKf07ef432529eb48dfa41c8033d15b21a,SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK90db90a49a7de047827bb0d1fcef2591,SIP/2.0/UDP sipserver:5050;branch=z9hG4bK-a1c3af132cd3579aff58b10b17402e11;received=127.0.0.1\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 5\r\n" +
                "\r\n" +
                "hallo").getBytes();

        semaphore.acquire();
        int bytesSent = 0;
        client.getOutputStream().write(doubleMessageRequest, bytesSent, 2);
        client.getOutputStream().flush();
        bytesSent += 2;
        Thread.sleep(300);
        semaphore.release();
        Thread.sleep(300);


        semaphore.acquire();
        client.getOutputStream().write(doubleMessageRequest, bytesSent, 7);
        client.getOutputStream().flush();
        bytesSent += 7;
        Thread.sleep(300);
        semaphore.release();
        Thread.sleep(300);

        semaphore.acquire();
        client.getOutputStream().write(doubleMessageRequest, bytesSent, 31);
        client.getOutputStream().flush();
        bytesSent += 31;
        Thread.sleep(300);
        semaphore.release();
        Thread.sleep(300);

        semaphore.acquire();
        client.getOutputStream().write(doubleMessageRequest, bytesSent, doubleMessageRequest.length - bytesSent);
        client.getOutputStream().flush();
        bytesSent = doubleMessageRequest.length;
        Thread.sleep(300);
        semaphore.release();
        Thread.sleep(300);

        Message register;
        assertNotNull(register = (Message) receivedRequests.take());
        assertTrue(register.getBody().length == 5);
        assertNotNull(register = (Message) receivedRequests.take());
        assertTrue(register.getBody().length == 5);
    }
}
