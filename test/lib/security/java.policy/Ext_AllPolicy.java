/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * This is a trivial file to grab the security manager, grab a
 * permission object, then try to check the resulting permission.
 */

import java.io.*;
import java.security.*;

public class Ext_AllPolicy {
    public static void main (String[] args) {
        boolean allPerms = args.length == 1 && args[0].equals("AllPermission");
        FilePermission mine = new FilePermission("/tmp/bar", "read");
        SecurityManager sm = System.getSecurityManager();

        if (sm != null) {
            try {
                sm.checkPermission(mine);
                if (!allPerms) {
                    // Default has no privilege.
                    throw new RuntimeException(mine + " expected to deny access");
                }
            } catch (AccessControlException e) {
                if (allPerms) {
                    // expected all permissions granted
                    throw e;
                }
            }
        }
    }
}
