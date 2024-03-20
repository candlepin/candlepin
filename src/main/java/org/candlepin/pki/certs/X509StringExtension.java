/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package org.candlepin.pki.certs;

import org.candlepin.pki.X509Extension;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;

import java.util.Objects;


public class X509StringExtension implements X509Extension {
    private final ASN1ObjectIdentifier oid;
    private final DERUTF8String value;
    private final boolean critical;

    public X509StringExtension(String oid, String value, boolean critical) {
        if (oid == null || oid.isEmpty()) {
            throw new IllegalArgumentException("oid is null or empty");
        }

        this.oid = new ASN1ObjectIdentifier(oid);
        this.critical = critical;
        // Bouncycastle hates null values. So, set them to blank if they are null
        this.value = new DERUTF8String(value == null ? "" : value);
    }

    public X509StringExtension(String oid, String value) {
        this(oid, value, false);
    }

    @Override
    public ASN1ObjectIdentifier oid() {
        return this.oid;
    }

    @Override
    public ASN1Encodable value() {
        return this.value;
    }

    @Override
    public boolean critical() {
        return this.critical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        X509StringExtension that = (X509StringExtension) o;
        return critical == that.critical &&
            Objects.equals(oid, that.oid) &&
            Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oid, value, critical);
    }

    @Override
    public String toString() {
        return "X509StringExtension{" +
            "oid=" + oid +
            ", value=" + value +
            ", critical=" + critical +
            '}';
    }
}
