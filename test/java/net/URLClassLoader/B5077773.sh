#! /bin/sh

#
# Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @author Michael McMahon
# @bug 5077773
# @summary Change in behaviour w.r.t jdk1.4.2 when loading resourcebundles
#
# ${TESTJAVA} is pointing to the jre
#
# set platform-dependent variables

OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    ;;
  Linux )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

cp ${TESTSRC}${FS}foo.jar .

${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}B5077773.java

WD=`pwd`
${TESTJAVA}${FS}bin${FS}java B5077773

