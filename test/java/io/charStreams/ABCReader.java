/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
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

/**/

import java.io.Reader;


class ABCReader extends Reader {

    int len;
    int count = 0;
    char next = 'a';

    ABCReader(int len) {
        this.len = len;
    }

    public int read() {
        if (count >= len)
            return -1;
        char c = next;
        if (next == 'z')
            next = '0';
        else if (next == '9')
            next = 'a';
        else
            next++;
        count++;
        return c;
    }

    public int read(char cbuf[], int off, int len) {
        for (int i = off; i < off + len; i++) {
            int c = read();
            if (c == -1) {
                if (i > off)
                    return i - off;
                else
                    return -1;
            }
            cbuf[i] = (char) c;
        }
        return len;
    }

    public void close() {
        len = 0;
    }

}
