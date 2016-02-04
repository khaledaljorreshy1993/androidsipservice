The Android SIP Service project is an effort to SIP-enable the Android platform in general.

Currently, the Android platform lacks an included SIP stack and thus any SIP enabled applications have to bring in their own. Running multiple applications which each have their own sip-stack and TCP connections open, consumes resources inefficiently.

This project aims to implement a [Service](http://developer.android.com/reference/android/app/Service.html) which runs in the background and manages the SIP protocol as efficient as possible on behalf of it's users. The Service will register itself to your ISP's SIP proxy (or IMS) and maintain this registration without draining the device's battery. It will also subscribe to your friend's presence state and provide messaging capabilities which are exposed
via an IPC API, such that third party (GUI) apps can use it.

For now, the project contains a working sip-stack, a msrp-stack, a shared TCP transport layer and a very basic SipService which just maintains a registration binding to show you how the sip-stack can be used.

If you want to help out or have any questions, please contact us : android(at)colibria.com