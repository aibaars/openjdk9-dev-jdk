#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6712755
# @summary jarsigner fails to sign itextasian.jar since 1.5.0_b14, it works with 1.5.0_13
#
# @run shell emptymanifest.sh
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Windows_* )
    FS="\\"
    ;;
  * )
    FS="/"
    ;;
esac

KS=emptymanifest.jks
JFILE=em.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore $KS"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER=$TESTJAVA${FS}bin${FS}jarsigner

rm $KS $JFILE
echo A > A
echo B > B
mkdir META-INF
printf "\r\n" > META-INF${FS}MANIFEST.MF
zip $JFILE META-INF${FS}MANIFEST.MF A B

$KT -alias a -dname CN=a -keyalg rsa -genkey -validity 300

$JARSIGNER -keystore $KS -storepass changeit $JFILE a || exit 1
$JARSIGNER -keystore $KS -verify -debug -strict $JFILE || exit 2

exit 0
