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
import org.bouncycastle.asn1.DEROctetString;

import java.util.Objects;

public class X509ByteExtension implements X509Extension {
    private final ASN1ObjectIdentifier oid;
    private final DEROctetString value;
    private final boolean critical;

    public X509ByteExtension(String oid, byte[] value, boolean critical) {
        if (oid == null || oid.isEmpty()) {
            throw new IllegalArgumentException("oid is null or empty");
        }

        this.oid = new ASN1ObjectIdentifier(oid);
        this.critical = critical;
        // Bouncycastle hates null values. So, set them to blank if they are null
        this.value = new DEROctetString(value == null ? new byte[0] : value);
    }

    public X509ByteExtension(String oid, byte[] value) {
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
        X509ByteExtension that = (X509ByteExtension) o;
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
        return "X509ByteExtension{" +
            "oid=" + oid +
            ", value=" + value +
            ", critical=" + critical +
            '}';
    }
}
