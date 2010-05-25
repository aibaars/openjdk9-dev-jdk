/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 4431020
 * @summary On Windows 2000 we observed behaviour that reflects the underlying
 * implementation :-
 *   1. PortUnreachableException throw per underlying reset
 *   2. No PUE throw for DatagramSocket.send
 */
import java.net.*;

public class OneExceptionOnly {

    static void doTest(InetAddress ia, int port, boolean testSend) throws Exception {

        System.out.println("");
        System.out.println("***");
        System.out.println("Test Description:");
        System.out.println("    - Send 10 datagrams to bad destination");
        System.out.println("    - <wait a wee while>");
        if (testSend) {
            System.out.println("    - Send another datagram - should throw PUE or timeout");
        } else {
            System.out.println("    - Receive another datagram - should throw PUE or timeout");
        }
        System.out.println("    - Receive another receive - a SocketTimeoutException expected");
        System.out.println("");

        /*
         * Create the datagram and connect it to destination
         */
        DatagramSocket s1 = new DatagramSocket();
        s1.connect(ia, port);

        byte b[] = new byte[512];
        DatagramPacket p = new DatagramPacket(b, b.length);

        /*
         * Send a bunch of packets to the destination
         */
        int outstanding = 0;
        for (int i=0; i<20; i++) {
            try {
                s1.send(p);
                outstanding++;
            } catch (PortUnreachableException e) {

                /* PUE throw => assume none outstanding now */
                outstanding = 0;
            }
            if (outstanding > 1) {
                break;
            }
        }
        if (outstanding < 1) {
            System.out.println("Insufficient exceptions outstanding - Test Skipped (Passed).");
            s1.close();
            return;
        }

        /*
         * Give time for ICMP port unreachables to return
         */
        Thread.currentThread().sleep(5000);

        /*
         * The next send or receive should cause a PUE to be thrown
         */
        boolean gotPUE = false;
        boolean gotTimeout = false;
        s1.setSoTimeout(2000);
        try {
            if (testSend) {
                s1.send(p);
            } else {
                s1.receive(p);
            }
        } catch (PortUnreachableException pue) {
            gotPUE = true;
            System.out.println("Expected PortUnreachableException thrown - good!");
        } catch (SocketTimeoutException exc) {
        }

        /*
         * The next receive should timeout
         */
        if (gotPUE) {
            try {
                s1.receive(p);
            } catch (PortUnreachableException pue) {
                throw new Exception("Unexpected PUE received - assumed that PUs would be consumed");
            } catch (SocketTimeoutException exc) {
                System.out.println("Expected SocketTimeoutException thrown - excellent! - Test Passed.");
            }
        } else {
            System.out.println("Expected PUE not thrown - packets probably discarded (Passed).");
        }

        s1.close();
    }


    public static void main(String args[]) throws Exception {

        InetAddress ia;
        int port;
        if (args.length >= 2) {
            ia = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
        } else {
            ia = InetAddress.getLocalHost();
            DatagramSocket s1 = new DatagramSocket();
            port = s1.getLocalPort();
            s1.close();
        }

        doTest(ia, port, true);
        doTest(ia, port, false);

    }

}
