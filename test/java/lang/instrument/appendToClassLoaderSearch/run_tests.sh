#!/bin/sh

#
# Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6173575 6388987
# @summary Unit tests for appendToBootstrapClassLoaderSearch and
#   appendToSystemClasLoaderSearch methods.
#
# @build Agent AgentSupport BootSupport BasicTest PrematureLoadTest DynamicTest
# @run shell run_tests.sh

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

. ${TESTSRC}/CommonSetup.sh


# Simple tests

echo "Creating jar files for simple tests..."

cd ${TESTCLASSES}

"$JAR" -cfm Agent.jar "${TESTSRC}"/manifest.mf Agent.class
"$JAR" -cf  AgentSupport.jar AgentSupport.class
"$JAR" -cf  BootSupport.jar BootSupport.class
"$JAR" -cf  SimpleTests.jar BasicTest.class PrematureLoadTest.class

failures=0

go() {
    echo ''
    sh -xc "$JAVA ${TESTVMOPTS} -javaagent:Agent.jar -classpath SimpleTests.jar  $1 $2 $3" 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

go BasicTest
go PrematureLoadTest

# Functional tests

echo ''
echo "Setup for functional tests..."

# Create org.tools.Tracer in temp directory so that it's not seen on the
# system class path

mkdir tmp
"${JAVAC}" -d tmp "${TESTSRC}"/Tracer.java
(cd tmp; "${JAR}" cf ../Tracer.jar org/tools/Tracer.class)

# InstrumentedApplication is Application+instrmentation - don't copy as
# we don't want the original file permission

cat "${TESTSRC}"/InstrumentedApplication.java > ./Application.java
"${JAVAC}" -classpath Tracer.jar -d . Application.java
mv Application.class InstrumentedApplication.bytes

cp "${TESTSRC}"/Application.java .
"${JAVAC}" -d . Application.java

sh -xc "$JAVA ${TESTVMOPTS} -classpath . -javaagent:Agent.jar DynamicTest" 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Repeat test with security manager
sh -xc "$JAVA ${TESTVMOPTS} -classpath . -javaagent:Agent.jar -Djava.security.manager DynamicTest" 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures
