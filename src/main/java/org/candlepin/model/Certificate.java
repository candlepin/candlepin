/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import org.candlepin.service.model.CertificateInfo;

import org.hibernate.annotations.GenericGenerator;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;



/**
 * Something something not-a-cert, db container for x509 cert, private key, and payload. Write a
 * better version of this.
 */
@Entity
@Table(name = Certificate.DB_TABLE)
public class Certificate extends AbstractHibernateObject<Certificate> implements CertificateInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_certificates";

    public static enum Type {
        UNKNOWN,
        CDN,
        CONTENT_ACCESS,
        ENTITLEMENT,
        IDENTITY,
        PRODUCT,
        UEBER,
        UPSTREAM_ENTITLEMENT

        // We could put handy things in here like "revocable" or ... actually that's probably all.
    }

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "serial", nullable = false)
    private BigInteger serial;

    @Column(name = "certificate", nullable = false)
    private byte[] certificate;

    @Column(name = "private_key", nullable = false)
    private byte[] privateKey;

    @Column(name = "payload", nullable = true)
    private byte[] payload;

    @Column(name = "expiration", nullable = false)
    private Instant expiration;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;


    public Certificate() {
        this.type = Type.UNKNOWN;
    }

    public String getId() {
        return this.id;
    }

    public Certificate setId(String id) {
        this.id = id;
        return this;
    }

    public Type getType() {
        return this.type;
    }

    public Certificate setType(Type type) {
        this.type = type != null ? type : Type.UNKNOWN;
        return this;
    }

    public BigInteger getSerial() {
        return this.serial;
    }

    public Certificate setSerial(BigInteger serial) {
        this.serial = serial;
        return this;
    }

    public byte[] getCertificate() {
        // potentially dangerous -- arrays cannot be made immutable
        return this.certificate;
    }

    public String getCertificateAsString() {
        return this.certificate != null ? new String(this.certificate, StandardCharsets.UTF_8) : null;
    }

    public Certificate setCertificate(byte[] certificate) {
        this.certificate = certificate;
        return this;
    }

    public Certificate setCertificate(String certificate) {
        this.certificate = certificate != null ? certificate.getBytes() : null;
        return this;
    }

    public byte[] getPrivateKey() {
        // potentially dangerous -- arrays cannot be made immutable
        return this.privateKey;
    }

    public String getPrivateKeyAsString() {
        return this.privateKey != null ? new String(this.privateKey, StandardCharsets.UTF_8) : null;
    }

    public Certificate setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public Certificate setPrivateKey(String privateKey) {
        this.privateKey = privateKey != null ? privateKey.getBytes() : null;
        return this;
    }

    public byte[] getPayload() {
        // potentially dangerous -- arrays cannot be made immutable
        return this.payload;
    }

    public String getPayloadAsString() {
        return this.payload != null ? new String(this.payload, StandardCharsets.UTF_8) : null;
    }

    public Certificate setPayload(byte[] payload) {
        this.payload = payload;
        return this;
    }

    public Certificate setPayload(String payload) {
        this.payload = payload != null ? payload.getBytes() : null;
        return this;
    }

    public Instant getExpiration() {
        return this.expiration;
    }

    public Certificate setExpiration(Instant expiration) {
        if (expiration == null) {
            throw new IllegalArgumentException("expiration is null");
        }

        this.expiration = expiration;
        return this;
    }

    public Certificate setExpiration(Date expiration) {
        if (expiration == null) {
            throw new IllegalArgumentException("expiration is null");
        }

        return this.setExpiration(expiration.toInstant());
    }

    public boolean isExpired() {
        return this.expiration != null ? this.expiration.isBefore(Instant.now()) : false;
    }

    public Boolean isRevoked() {
        return this.revoked;
    }

    public Certificate setRevoked(boolean revoked) {
        this.revoked = revoked;
        return this;
    }

    public Certificate setRevoked(Boolean revoked) {
        // Safe auto-unboxing
        return revoked != null ? this.setRevoked(revoked.booleanValue()) : this;
    }

    @Override
    public String toString() {
        return String.format("Certificate [id: %s, serial: %s, cert: %s, pkey: %s, payload: %s, revoked: %b]",
            this.getId(), this.getSerial(), this.printArray(this.getCertificate()),
            this.printArray(this.getPrivateKey()), this.printArray(this.getPayload()), this.isRevoked());
    }

    private String printArray(byte[] arr) {
        if (arr == null) {
            return "null";
        }

        return String.format("<%d byte(s)>", arr.length);
    }
}
