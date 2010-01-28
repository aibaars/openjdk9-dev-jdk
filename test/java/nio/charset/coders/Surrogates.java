/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @summary Check that array-crossing surrogate pairs are handled properly
 */

import java.io.*;
import java.nio.*;
import java.nio.charset.*;


public class Surrogates {

    static PrintStream log = System.err;

    static char[] input;
    static byte[] output;

    static final int LEN = 128;

    static void initData() throws IOException {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < LEN; i++) {
            int c = Surrogate.UCS4_SURROGATE_MIN + 1;
            sb.append(Surrogate.high(c));
            sb.append(Surrogate.low(c));
        }
        input = sb.toString().toCharArray();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStreamWriter osw
            = new OutputStreamWriter(bos, Charset.forName("UTF-8"));
        osw.write(input);
        osw.close();
        output = bos.toByteArray();
    }

    static void testLeftovers(boolean doMalformed)
        throws Exception
    {
        log.print("Leftover surrogates, doMalformed = " + doMalformed);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStreamWriter osw
            = new OutputStreamWriter(bos, Charset.forName("UTF-8"));
        for (int i = 0; i < input.length; i += 7)
            osw.write(input, i, Math.min(input.length - i, 7));
        if (doMalformed)
            osw.write(input, 0, 1);
        osw.close();
        byte[] result = bos.toByteArray();

        // Ignore a trailing '?' if we wrote a malformed final surrogate
        int rl = result.length + (doMalformed ? -1 : 0);

        if (rl != output.length)
            throw new Exception("Incorrect result length "
                                + rl + ", expected " + output.length);
        for (int i = 0; i < output.length; i++)
            if (result[i] != output[i])
                throw new Exception("Unexpected result value at index " + i);
        log.println(": Passed");
    }

    public static void main(String[] args) throws Exception {
        initData();
        testLeftovers(false);
        testLeftovers(true);
    }

}
