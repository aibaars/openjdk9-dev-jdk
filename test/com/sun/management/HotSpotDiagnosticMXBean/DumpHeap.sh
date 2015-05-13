#!/bin/sh

#
# Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#


# @test
# @bug 6455258
# @summary Sanity test for com.sun.management.HotSpotDiagnosticMXBean.dumpHeap 
#          method 
#
# @modules jdk.management
# @build DumpHeap
# @run shell DumpHeap.sh

if [ "${TESTJAVA}" = "" ] ; then
     echo "--Error: TESTJAVA must be defined as the pathname of a jdk to test."
     exit 1
fi

if [ "${COMPILEJAVA}" = "" ] ; then
    COMPILEJAVA="${TESTJAVA}"
fi

failed=0

# we use the pid of this shell process to name the heap dump output file.
DUMPFILE="java_pid$$.hprof"

${TESTJAVA}/bin/java ${TESTVMOPTS} -classpath $TESTCLASSES \
    DumpHeap ${DUMPFILE} || exit 2

# check that heap dump is parsable
${COMPILEJAVA}/bin/jhat ${TESTTOOLVMOPTS} -parseonly true ${DUMPFILE}
if [ $? != 0 ]; then failed=1; fi

# dump file is large so remove it
rm ${DUMPFILE}

exit $failed