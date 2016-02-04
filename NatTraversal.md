# TCP transport #
The SIP- and MSRP-stack included in this project use the common module "comp-tcp-tl" which provides the transport layer based on TCP. This module is implemented such that it only supports outbound connections (thus doesn't listen on any port for incoming connections) because those stack's are intended to be used for clients only which are behind NAT in most cases. UDP is NAT unfriendly and is therefore not included at all. If UDP would have been used behind NAT, then the client would have to send keep-alives frequently to keep the NAT context open (in order to be able to receive new requests from the server) which would drain the device's battery. So we didn't even bother adding UDP support.

# SIP NAT Traversal #
SIP is a protocol which creates TCP connections dynamically, based on the routing information found in the SIP message itself. When a client sends a request to a server, it would put information about itself in the message (Via header) such that the server knows where to send the response to. However, since the client is often behind NAT and therefore doesn't know it's public address/port combination, it is unable to populate this information in the request. This problem has been solved in [RFC3581](http://www.faqs.org/rfcs/rfc3581.html) by defining the "rport" and "received" parameters. The SIP-stack included in this project is compliant to this specification.

Besides routing responses through a NAT correctly back to the client, there is also the problem of receiving new unrelated initial requests from the server. If a server wants to send a new request to the client, the server would typically use the routing information which it finds in the registration binding for this particular user. The registration binding is installed at the server-side by the client itself by sending a REGISTER request. In other words, the client tells the server about its location. Again, since the client doesn't know it's public address/port combination, it is unable to install the correct binding using REGISTER. This problem is solved by making use of the specification [draft-ietf-sip-connect-reuse](http://tools.ietf.org/html/draft-ietf-sip-connect-reuse-14).

This specification adds a mapping between routing information and existing TCP connections. The sip-stack included in this project makes use of this in the following way: When the clients sends its first request over a newly established TCP connection, it would use a globally unique hostname/port combination in the Via header like this:

```
REGISTER sip:proxy.example.com SIP/2.0 
Via: SIP/2.0/TCP e003XGBBy4.localhost:5060;branch=z9hG4bKnashds7;alias;rport
Max-Forwards: 70 
To: Bob <sip:bob@example.com>
From: Bob <sip:bob@example.com>;tag=456248
Call-ID: 843817637684230@998sdasdh09
CSeq: 1826 REGISTER
Contact: <sip:bob@e003XGBBy4.localhost:5060> 
Expires: 7200 
Content-Length: 0
```

**e003XGBBy4.localhost** is a non existing hostname, which is fine since the client doesn't know its public hostname/port combination anyway. Once the server's stack receives this request, it will add the actual source IP and port from which it had received this request (for example: 212.212.212.212:8492) to the message like this:

```
REGISTER sip:proxy.example.com SIP/2.0 
Via: SIP/2.0/TCP e003XGBBy4.localhost:5060;branch=z9hG4bKnashds7;alias;rport=8492;received=212.212.212.212
Max-Forwards: 70 
To: Bob <sip:bob@example.com>
From: Bob <sip:bob@example.com>;tag=456248
Call-ID: 843817637684230@998sdasdh09
CSeq: 1826 REGISTER
Contact: <sip:bob@e003XGBBy4.localhost:5060> 
Expires: 7200 
Content-Length: 0
```

and it will also remember that address "e003XGBBy4.localhost:5060" is located behind the existing TCP connection towards "212.212.212.212:8492". The registrar then creates a binding towards <sip:bob@e003XGBBy4.localhost:5060> for the user identity <sip:bob@example.com>.

If the server wishes to send a new initial request to Bob, it will place the URI sip:bob@e003XGBBy4.localhost:5060 in the request which it found in the binding. When the request is sent, the server's stack (compliant to the  then [draft-ietf-sip-connect-reuse](http://tools.ietf.org/html/draft-ietf-sip-connect-reuse-14) specifications) will re-use the existing connection "212.212.212.212:8492" to send this request. Example:

```
INVITE sip:bob@example.com SIP/2.0 
Via: SIP/2.0/TCP proxy.example.com:5060;branch=z9hG4bKnashds8
Max-Forwards: 70 
To: Bob <sip:bob@example.com>
From: Bob <sip:bob@example.com>;tag=456249
Call-ID: 843817637684230@998sdasdh10
CSeq: 1 INVITE
Route: <sip:bob@e003XGBBy4.localhost:5060>
Contact: <sip:proxy.example.com:5060>
Content-Length: 0
```