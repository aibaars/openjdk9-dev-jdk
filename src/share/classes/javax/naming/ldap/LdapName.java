/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package javax.naming.ldap;

import javax.naming.Name;
import javax.naming.InvalidNameException;

import java.util.Enumeration;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Collections;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 * This class represents a distinguished name as specified by
 * <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>.
 * A distinguished name, or DN, is composed of an ordered list of
 * components called <em>relative distinguished name</em>s, or RDNs.
 * Details of a DN's syntax are described in RFC 2253.
 *<p>
 * This class resolves a few ambiguities found in RFC 2253
 * as follows:
 * <ul>
 * <li> RFC 2253 leaves the term "whitespace" undefined. The
 *      ASCII space character 0x20 (" ") is used in its place.
 * <li> Whitespace is allowed on either side of ',', ';', '=', and '+'.
 *      Such whitespace is accepted but not generated by this code,
 *      and is ignored when comparing names.
 * <li> AttributeValue strings containing '=' or non-leading '#'
 *      characters (unescaped) are accepted.
 * </ul>
 *<p>
 * String names passed to <code>LdapName</code> or returned by it
 * use the full Unicode character set. They may also contain
 * characters encoded into UTF-8 with each octet represented by a
 * three-character substring such as "\\B4".
 * They may not, however, contain characters encoded into UTF-8 with
 * each octet represented by a single character in the string:  the
 * meaning would be ambiguous.
 *<p>
 * <code>LdapName</code> will properly parse all valid names, but
 * does not attempt to detect all possible violations when parsing
 * invalid names.  It is "generous" in accepting invalid names.
 * The "validity" of a name is determined ultimately when it
 * is supplied to an LDAP server, which may accept or
 * reject the name based on factors such as its schema information
 * and interoperability considerations.
 *<p>
 * When names are tested for equality, attribute types, both binary
 * and string values, are case-insensitive.
 * String values with different but equivalent usage of quoting,
 * escaping, or UTF8-hex-encoding are considered equal.  The order of
 * components in multi-valued RDNs (such as "ou=Sales+cn=Bob") is not
 * significant.
 * <p>
 * The components of a LDAP name, that is, RDNs, are numbered. The
 * indexes of a LDAP name with n RDNs range from 0 to n-1.
 * This range may be written as [0,n).
 * The right most RDN is at index 0, and the left most RDN is at
 * index n-1. For example, the distinguished name:
 * "CN=Steve Kille, O=Isode Limited, C=GB" is numbered in the following
 * sequence ranging from 0 to 2: {C=GB, O=Isode Limited, CN=Steve Kille}. An
 * empty LDAP name is represented by an empty RDN list.
 *<p>
 * Concurrent multithreaded read-only access of an instance of
 * <tt>LdapName</tt> need not be synchronized.
 *<p>
 * Unless otherwise noted, the behavior of passing a null argument
 * to a constructor or method in this class will cause a
 * NullPointerException to be thrown.
 *
 * @author Scott Seligman
 * @since 1.5
 */

public class LdapName implements Name {

    private transient List<Rdn> rdns;   // parsed name components
    private transient String unparsed;  // if non-null, the DN in unparsed form
    private static final long serialVersionUID = -1595520034788997356L;

    /**
     * Constructs an LDAP name from the given distinguished name.
     *
     * @param name  This is a non-null distinguished name formatted
     * according to the rules defined in
     * <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>.
     *
     * @throws InvalidNameException if a syntax violation is detected.
     * @see Rdn#escapeValue(Object value)
     */
    public LdapName(String name) throws InvalidNameException {
        unparsed = name;
        parse();
    }

    /**
     * Constructs an LDAP name given its parsed RDN components.
     * <p>
     * The indexing of RDNs in the list follows the numbering of
     * RDNs described in the class description.
     *
     * @param rdns The non-null list of <tt>Rdn</tt>s forming this LDAP name.
     */
    public LdapName(List<Rdn> rdns) {

        // if (rdns instanceof ArrayList<Rdn>) {
        //      this.rdns = rdns.clone();
        // } else if (rdns instanceof List<Rdn>) {
        //      this.rdns = new ArrayList<Rdn>(rdns);
        // } else {
        //      throw IllegalArgumentException(
        //              "Invalid entries, list entries must be of type Rdn");
        //  }

        this.rdns = new ArrayList<>(rdns.size());
        for (int i = 0; i < rdns.size(); i++) {
            Object obj = rdns.get(i);
            if (!(obj instanceof Rdn)) {
                throw new IllegalArgumentException("Entry:" + obj +
                        "  not a valid type;list entries must be of type Rdn");
            }
            this.rdns.add((Rdn)obj);
        }
    }

    /*
     * Constructs an LDAP name given its parsed components (the elements
     * of "rdns" in the range [beg,end)) and, optionally
     * (if "name" is not null), the unparsed DN.
     *
     */
    private LdapName(String name, List<Rdn> rdns, int beg, int end) {
        unparsed = name;
        // this.rdns = rdns.subList(beg, end);

        List<Rdn> sList = rdns.subList(beg, end);
        this.rdns = new ArrayList<>(sList);
    }

    /**
     * Retrieves the number of components in this LDAP name.
     * @return The non-negative number of components in this LDAP name.
     */
    public int size() {
        return rdns.size();
    }

    /**
     * Determines whether this LDAP name is empty.
     * An empty name is one with zero components.
     * @return true if this LDAP name is empty, false otherwise.
     */
    public boolean isEmpty() {
        return rdns.isEmpty();
    }

    /**
     * Retrieves the components of this name as an enumeration
     * of strings. The effect of updates to this name on this enumeration
     * is undefined. If the name has zero components, an empty (non-null)
     * enumeration is returned.
     * The order of the components returned by the enumeration is same as
     * the order in which the components are numbered as described in the
     * class description.
     *
     * @return A non-null enumeration of the components of this LDAP name.
     * Each element of the enumeration is of class String.
     */
    public Enumeration<String> getAll() {
        final Iterator<Rdn> iter = rdns.iterator();

        return new Enumeration<String>() {
            public boolean hasMoreElements() {
                return iter.hasNext();
            }
            public String nextElement() {
                return iter.next().toString();
            }
        };
    }

    /**
     * Retrieves a component of this LDAP name as a string.
     * @param  posn The 0-based index of the component to retrieve.
     *              Must be in the range [0,size()).
     * @return The non-null component at index posn.
     * @exception IndexOutOfBoundsException if posn is outside the
     *          specified range.
     */
    public String get(int posn) {
        return rdns.get(posn).toString();
    }

    /**
     * Retrieves an RDN of this LDAP name as an Rdn.
     * @param   posn The 0-based index of the RDN to retrieve.
     *          Must be in the range [0,size()).
     * @return The non-null RDN at index posn.
     * @exception IndexOutOfBoundsException if posn is outside the
     *            specified range.
     */
    public Rdn getRdn(int posn) {
        return rdns.get(posn);
    }

    /**
     * Creates a name whose components consist of a prefix of the
     * components of this LDAP name.
     * Subsequent changes to this name will not affect the name
     * that is returned and vice versa.
     * @param  posn     The 0-based index of the component at which to stop.
     *                  Must be in the range [0,size()].
     * @return  An instance of <tt>LdapName</tt> consisting of the
     *          components at indexes in the range [0,posn).
     *          If posn is zero, an empty LDAP name is returned.
     * @exception   IndexOutOfBoundsException
     *              If posn is outside the specified range.
     */
    public Name getPrefix(int posn) {
        try {
            return new LdapName(null, rdns, 0, posn);
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException(
                "Posn: " + posn + ", Size: "+ rdns.size());
        }
    }

    /**
     * Creates a name whose components consist of a suffix of the
     * components in this LDAP name.
     * Subsequent changes to this name do not affect the name that is
     * returned and vice versa.
     *
     * @param  posn     The 0-based index of the component at which to start.
     *                  Must be in the range [0,size()].
     * @return  An instance of <tt>LdapName</tt> consisting of the
     *          components at indexes in the range [posn,size()).
     *          If posn is equal to size(), an empty LDAP name is
     *          returned.
     * @exception IndexOutOfBoundsException
     *          If posn is outside the specified range.
     */
    public Name getSuffix(int posn) {
        try {
            return new LdapName(null, rdns, posn, rdns.size());
        } catch (IllegalArgumentException e) {
            throw new IndexOutOfBoundsException(
                "Posn: " + posn + ", Size: "+ rdns.size());
        }
    }

    /**
     * Determines whether this LDAP name starts with a specified LDAP name
     * prefix.
     * A name <tt>n</tt> is a prefix if it is equal to
     * <tt>getPrefix(n.size())</tt>--in other words this LDAP
     * name starts with 'n'. If n is null or not a RFC2253 formatted name
     * as described in the class description, false is returned.
     *
     * @param n The LDAP name to check.
     * @return  true if <tt>n</tt> is a prefix of this LDAP name,
     * false otherwise.
     * @see #getPrefix(int posn)
     */
    public boolean startsWith(Name n) {
        if (n == null) {
            return false;
        }
        int len1 = rdns.size();
        int len2 = n.size();
        return (len1 >= len2 &&
                matches(0, len2, n));
    }

    /**
     * Determines whether the specified RDN sequence forms a prefix of this
     * LDAP name.  Returns true if this LdapName is at least as long as rdns,
     * and for every position p in the range [0, rdns.size()) the component
     * getRdn(p) matches rdns.get(p). Returns false otherwise. If rdns is
     * null, false is returned.
     *
     * @param rdns The sequence of <tt>Rdn</tt>s to check.
     * @return  true if <tt>rdns</tt> form a prefix of this LDAP name,
     *          false otherwise.
     */
    public boolean startsWith(List<Rdn> rdns) {
        if (rdns == null) {
            return false;
        }
        int len1 = this.rdns.size();
        int len2 = rdns.size();
        return (len1 >= len2 &&
                doesListMatch(0, len2, rdns));
    }

    /**
     * Determines whether this LDAP name ends with a specified
     * LDAP name suffix.
     * A name <tt>n</tt> is a suffix if it is equal to
     * <tt>getSuffix(size()-n.size())</tt>--in other words this LDAP
     * name ends with 'n'. If n is null or not a RFC2253 formatted name
     * as described in the class description, false is returned.
     *
     * @param n The LDAP name to check.
     * @return true if <tt>n</tt> is a suffix of this name, false otherwise.
     * @see #getSuffix(int posn)
     */
    public boolean endsWith(Name n) {
        if (n == null) {
            return false;
        }
        int len1 = rdns.size();
        int len2 = n.size();
        return (len1 >= len2 &&
                matches(len1 - len2, len1, n));
    }

    /**
     * Determines whether the specified RDN sequence forms a suffix of this
     * LDAP name.  Returns true if this LdapName is at least as long as rdns,
     * and for every position p in the range [size() - rdns.size(), size())
     * the component getRdn(p) matches rdns.get(p). Returns false otherwise.
     * If rdns is null, false is returned.
     *
     * @param rdns The sequence of <tt>Rdn</tt>s to check.
     * @return  true if <tt>rdns</tt> form a suffix of this LDAP name,
     *          false otherwise.
     */
    public boolean endsWith(List<Rdn> rdns) {
        if (rdns == null) {
            return false;
        }
        int len1 = this.rdns.size();
        int len2 = rdns.size();
        return (len1 >= len2 &&
                doesListMatch(len1 - len2, len1, rdns));
    }

    private boolean doesListMatch(int beg, int end, List<Rdn> rdns) {
        for (int i = beg; i < end; i++) {
            if (!this.rdns.get(i).equals(rdns.get(i - beg))) {
                return false;
            }
        }
        return true;
    }

    /*
     * Helper method for startsWith() and endsWith().
     * Returns true if components [beg,end) match the components of "n".
     * If "n" is not an LdapName, each of its components is parsed as
     * the string form of an RDN.
     * The following must hold:  end - beg == n.size().
     */
    private boolean matches(int beg, int end, Name n) {
        if (n instanceof LdapName) {
            LdapName ln = (LdapName) n;
            return doesListMatch(beg, end, ln.rdns);
        } else {
            for (int i = beg; i < end; i++) {
                Rdn rdn;
                String rdnString = n.get(i - beg);
                try {
                    rdn = (new Rfc2253Parser(rdnString)).parseRdn();
                } catch (InvalidNameException e) {
                    return false;
                }
                if (!rdn.equals(rdns.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Adds the components of a name -- in order -- to the end of this name.
     *
     * @param   suffix The non-null components to add.
     * @return  The updated name (not a new instance).
     *
     * @throws  InvalidNameException if <tt>suffix</tt> is not a valid LDAP
     *          name, or if the addition of the components would violate the
     *          syntax rules of this LDAP name.
     */
    public Name addAll(Name suffix) throws InvalidNameException {
         return addAll(size(), suffix);
    }


    /**
     * Adds the RDNs of a name -- in order -- to the end of this name.
     *
     * @param   suffixRdns The non-null suffix <tt>Rdn</tt>s to add.
     * @return  The updated name (not a new instance).
     */
    public Name addAll(List<Rdn> suffixRdns) {
        return addAll(size(), suffixRdns);
    }

    /**
     * Adds the components of a name -- in order -- at a specified position
     * within this name. Components of this LDAP name at or after the
     * index (if any) of the first new component are shifted up
     * (away from index 0) to accomodate the new components.
     *
     * @param suffix    The non-null components to add.
     * @param posn      The index at which to add the new component.
     *                  Must be in the range [0,size()].
     *
     * @return  The updated name (not a new instance).
     *
     * @throws  InvalidNameException if <tt>suffix</tt> is not a valid LDAP
     *          name, or if the addition of the components would violate the
     *          syntax rules of this LDAP name.
     * @throws  IndexOutOfBoundsException
     *          If posn is outside the specified range.
     */
    public Name addAll(int posn, Name suffix)
        throws InvalidNameException {
        unparsed = null;        // no longer valid
        if (suffix instanceof LdapName) {
            LdapName s = (LdapName) suffix;
            rdns.addAll(posn, s.rdns);
        } else {
            Enumeration<String> comps = suffix.getAll();
            while (comps.hasMoreElements()) {
                rdns.add(posn++,
                    (new Rfc2253Parser(comps.nextElement()).
                    parseRdn()));
            }
        }
        return this;
    }

    /**
     * Adds the RDNs of a name -- in order -- at a specified position
     * within this name. RDNs of this LDAP name at or after the
     * index (if any) of the first new RDN are shifted up (away from index 0) to
     * accomodate the new RDNs.
     *
     * @param suffixRdns        The non-null suffix <tt>Rdn</tt>s to add.
     * @param posn              The index at which to add the suffix RDNs.
     *                          Must be in the range [0,size()].
     *
     * @return  The updated name (not a new instance).
     * @throws  IndexOutOfBoundsException
     *          If posn is outside the specified range.
     */
    public Name addAll(int posn, List<Rdn> suffixRdns) {
        unparsed = null;
        for (int i = 0; i < suffixRdns.size(); i++) {
            Object obj = suffixRdns.get(i);
            if (!(obj instanceof Rdn)) {
                throw new IllegalArgumentException("Entry:" + obj +
                "  not a valid type;suffix list entries must be of type Rdn");
            }
            rdns.add(i + posn, (Rdn)obj);
        }
        return this;
    }

    /**
     * Adds a single component to the end of this LDAP name.
     *
     * @param comp      The non-null component to add.
     * @return          The updated LdapName, not a new instance.
     *                  Cannot be null.
     * @exception       InvalidNameException If adding comp at end of the name
     *                  would violate the name's syntax.
     */
    public Name add(String comp) throws InvalidNameException {
        return add(size(), comp);
    }

    /**
     * Adds a single RDN to the end of this LDAP name.
     *
     * @param comp      The non-null RDN to add.
     *
     * @return          The updated LdapName, not a new instance.
     *                  Cannot be null.
     */
    public Name add(Rdn comp) {
        return add(size(), comp);
    }

    /**
     * Adds a single component at a specified position within this
     * LDAP name.
     * Components of this LDAP name at or after the index (if any) of the new
     * component are shifted up by one (away from index 0) to accommodate
     * the new component.
     *
     * @param  comp     The non-null component to add.
     * @param  posn     The index at which to add the new component.
     *                  Must be in the range [0,size()].
     * @return          The updated LdapName, not a new instance.
     *                  Cannot be null.
     * @exception       IndexOutOfBoundsException
     *                  If posn is outside the specified range.
     * @exception       InvalidNameException If adding comp at the
     *                  specified position would violate the name's syntax.
     */
    public Name add(int posn, String comp) throws InvalidNameException {
        Rdn rdn = (new Rfc2253Parser(comp)).parseRdn();
        rdns.add(posn, rdn);
        unparsed = null;        // no longer valid
        return this;
    }

    /**
     * Adds a single RDN at a specified position within this
     * LDAP name.
     * RDNs of this LDAP name at or after the index (if any) of the new
     * RDN are shifted up by one (away from index 0) to accommodate
     * the new RDN.
     *
     * @param  comp     The non-null RDN to add.
     * @param  posn     The index at which to add the new RDN.
     *                  Must be in the range [0,size()].
     * @return          The updated LdapName, not a new instance.
     *                  Cannot be null.
     * @exception       IndexOutOfBoundsException
     *                  If posn is outside the specified range.
     */
    public Name add(int posn, Rdn comp) {
        if (comp == null) {
            throw new NullPointerException("Cannot set comp to null");
        }
        rdns.add(posn, comp);
        unparsed = null;        // no longer valid
        return this;
    }

    /**
     * Removes a component from this LDAP name.
     * The component of this name at the specified position is removed.
     * Components with indexes greater than this position (if any)
     * are shifted down (toward index 0) by one.
     *
     * @param posn      The index of the component to remove.
     *                  Must be in the range [0,size()).
     * @return          The component removed (a String).
     *
     * @throws          IndexOutOfBoundsException
     *                  if posn is outside the specified range.
     * @throws          InvalidNameException if deleting the component
     *                  would violate the syntax rules of the name.
     */
    public Object remove(int posn) throws InvalidNameException {
        unparsed = null;        // no longer valid
        return rdns.remove(posn).toString();
    }

    /**
     * Retrieves the list of relative distinguished names.
     * The contents of the list are unmodifiable.
     * The indexing of RDNs in the returned list follows the numbering of
     * RDNs as described in the class description.
     * If the name has zero components, an empty list is returned.
     *
     * @return  The name as a list of RDNs which are instances of
     *          the class {@link Rdn Rdn}.
     */
    public List<Rdn> getRdns() {
        return Collections.unmodifiableList(rdns);
    }

    /**
     * Generates a new copy of this name.
     * Subsequent changes to the components of this name will not
     * affect the new copy, and vice versa.
     *
     * @return A copy of the this LDAP name.
     */
    public Object clone() {
        return new LdapName(unparsed, rdns, 0, rdns.size());
    }

    /**
     * Returns a string representation of this LDAP name in a format
     * defined by <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>
     * and described in the class description. If the name has zero
     * components an empty string is returned.
     *
     * @return The string representation of the LdapName.
     */
    public String toString() {
        if (unparsed != null) {
            return unparsed;
        }
        StringBuilder builder = new StringBuilder();
        int size = rdns.size();
        if ((size - 1) >= 0) {
            builder.append(rdns.get(size - 1));
        }
        for (int next = size - 2; next >= 0; next--) {
            builder.append(',');
            builder.append(rdns.get(next));
        }
        unparsed = builder.toString();
        return unparsed;
    }

    /**
     * Determines whether two LDAP names are equal.
     * If obj is null or not an LDAP name, false is returned.
     * <p>
     * Two LDAP names are equal if each RDN in one is equal
     * to the corresponding RDN in the other. This implies
     * both have the same number of RDNs, and each RDN's
     * equals() test against the corresponding RDN in the other
     * name returns true. See {@link Rdn#equals(Object obj)}
     * for a definition of RDN equality.
     *
     * @param  obj      The possibly null object to compare against.
     * @return          true if obj is equal to this LDAP name,
     *                  false otherwise.
     * @see #hashCode
     */
    public boolean equals(Object obj) {
        // check possible shortcuts
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof LdapName)) {
            return false;
        }
        LdapName that = (LdapName) obj;
        if (rdns.size() != that.rdns.size()) {
            return false;
        }
        if (unparsed != null && unparsed.equalsIgnoreCase(
                that.unparsed)) {
            return true;
        }
        // Compare RDNs one by one for equality
        for (int i = 0; i < rdns.size(); i++) {
            // Compare a single pair of RDNs.
            Rdn rdn1 = rdns.get(i);
            Rdn rdn2 = that.rdns.get(i);
            if (!rdn1.equals(rdn2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares this LdapName with the specified Object for order.
     * Returns a negative integer, zero, or a positive integer as this
     * Name is less than, equal to, or greater than the given Object.
     * <p>
     * If obj is null or not an instance of LdapName, ClassCastException
     * is thrown.
     * <p>
     * Ordering of LDAP names follows the lexicographical rules for
     * string comparison, with the extension that this applies to all
     * the RDNs in the LDAP name. All the RDNs are lined up in their
     * specified order and compared lexicographically.
     * See {@link Rdn#compareTo(Object obj) Rdn.compareTo(Object obj)}
     * for RDN comparison rules.
     * <p>
     * If this LDAP name is lexicographically lesser than obj,
     * a negative number is returned.
     * If this LDAP name is lexicographically greater than obj,
     * a positive number is returned.
     * @param obj The non-null LdapName instance to compare against.
     *
     * @return  A negative integer, zero, or a positive integer as this Name
     *          is less than, equal to, or greater than the given obj.
     * @exception ClassCastException if obj is null or not a LdapName.
     */
    public int compareTo(Object obj) {

        if (!(obj instanceof LdapName)) {
            throw new ClassCastException("The obj is not a LdapName");
        }

        // check possible shortcuts
        if (obj == this) {
            return 0;
        }
        LdapName that = (LdapName) obj;

        if (unparsed != null && unparsed.equalsIgnoreCase(
                        that.unparsed)) {
            return 0;
        }

        // Compare RDNs one by one, lexicographically.
        int minSize = Math.min(rdns.size(), that.rdns.size());
        for (int i = 0; i < minSize; i++) {
            // Compare a single pair of RDNs.
            Rdn rdn1 = rdns.get(i);
            Rdn rdn2 = that.rdns.get(i);

            int diff = rdn1.compareTo(rdn2);
            if (diff != 0) {
                return diff;
            }
        }
        return (rdns.size() - that.rdns.size());        // longer DN wins
    }

    /**
     * Computes the hash code of this LDAP name.
     * The hash code is the sum of the hash codes of individual RDNs
     * of this  name.
     *
     * @return An int representing the hash code of this name.
     * @see #equals
     */
    public int hashCode() {
        // Sum up the hash codes of the components.
        int hash = 0;

        // For each RDN...
        for (int i = 0; i < rdns.size(); i++) {
            Rdn rdn = rdns.get(i);
            hash += rdn.hashCode();
        }
        return hash;
    }

    /**
     * Serializes only the unparsed DN, for compactness and to avoid
     * any implementation dependency.
     *
     * @serialData      The DN string
     */
    private void writeObject(ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(toString());
    }

    private void readObject(ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        unparsed = (String)s.readObject();
        try {
            parse();
        } catch (InvalidNameException e) {
            // shouldn't happen
            throw new java.io.StreamCorruptedException(
                    "Invalid name: " + unparsed);
        }
    }

    private void parse() throws InvalidNameException {
        // rdns = (ArrayList<Rdn>) (new RFC2253Parser(unparsed)).getDN();

        rdns = new Rfc2253Parser(unparsed).parseDn();
    }
}
