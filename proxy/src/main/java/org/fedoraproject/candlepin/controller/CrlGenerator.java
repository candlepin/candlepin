/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.controller;

import static org.fedoraproject.candlepin.util.Util.getValue;
import static org.fedoraproject.candlepin.util.Util.newList;
import static org.fedoraproject.candlepin.util.Util.newMap;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.name.Named;

// TODO:  Clean up these protected methods - most are protected only for unit testing!
/**
 * CRLGenerator
 */
public class CrlGenerator {

    /** The pki reader. */
    private PKIReader pkiReader;
    
    /** The certificate serial curator. */
    private CertificateSerialCurator certificateSerialCurator;
    
    /** The algorithm. */
    private String algorithm;

    private static Logger log = Logger.getLogger(CrlGenerator.class); 
    /**
     * Instantiates a new certificate revocation list task.
     * 
     * @param rdr the rdr
     * @param curator the curator
     * @param algorithm the algorithm
     */
    @Inject
    public CrlGenerator(PKIReader rdr, CertificateSerialCurator curator, 
        @Named("crlSignatureAlgo") String algorithm) {
        
        this.pkiReader = rdr;
        this.certificateSerialCurator = curator;
        this.algorithm = algorithm;
    }

    /**
     * The Class SimpleCRLEntry.
     */
    protected static class SimpleCRLEntry{
        
        /** The serial number. */
        protected BigInteger serialNumber;
        
        /** The revocation date. */
        protected Date revocationDate;

        /**
         * Instantiates a new simple crl entry.
         * 
         * @param serialNumber the serial number
         * @param revocationDate the revocation date
         */
        private SimpleCRLEntry(BigInteger serialNumber, Date revocationDate) {
            this.serialNumber = serialNumber;
            this.revocationDate = revocationDate;
        }
    }

    /**
     * Update crl.
     * 
     * @param x509crl the x509crl
     * @return the x509 crl
     */
    public X509CRL updateCRL(X509CRL x509crl) {
        List<SimpleCRLEntry> crlEntries = null;
        BigInteger no = getCRLNumber(x509crl);
        log.info("Old CRLNumber is : " + no);
        
        if (x509crl != null) {
            crlEntries = this.toSimpleCRLEntries(removeExpiredSerials(x509crl
                .getRevokedCertificates()));
        }
        else {
            crlEntries = newList();
        }
        
        crlEntries.addAll(getNewSerialsToAppendAndSetThemConsumed());
        this.certificateSerialCurator.deleteExpiredSerials();
        
        return this.generateCRL(crlEntries, no
            .add(BigInteger.ONE));
    }
    
    /**
     * Generate a new CRL.
     * 
     * @return the x509 CRL
     */
    public X509CRL createCRL() {
        return updateCRL(null);
    }
    
    /**
     * Generate crl.
     * 
     * @param entries the entries
     * @param principal the principal
     * @return the x509 crl
     */
    private X509CRL generateCRL(List<SimpleCRLEntry> entries, BigInteger crlNumber) {
        
        try {
            X509Certificate caCert = pkiReader.getCACert();
            X509V2CRLGenerator generator = new X509V2CRLGenerator();
            generator.setIssuerDN(caCert.getIssuerX500Principal());
            generator.setThisUpdate(new Date());
            generator.setNextUpdate(Util.tomorrow());
            generator.setSignatureAlgorithm(algorithm);
            //add all the crl entries.
            for (SimpleCRLEntry entry : entries) {
                generator.addCRLEntry(entry.serialNumber, entry.revocationDate,
                    CRLReason.privilegeWithdrawn);
            }
            log.info("Completed adding CRL numbers to the certificate.");
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier,
                false, new AuthorityKeyIdentifierStructure(caCert));
            generator.addExtension(X509Extensions.CRLNumber, false,
                new CRLNumber(crlNumber));
            return generator.generate(pkiReader.getCaKey());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the new serials to append and set them consumed.
     * 
     * @return the new serials to append and set them consumed
     */
    protected List<SimpleCRLEntry> getNewSerialsToAppendAndSetThemConsumed() {
        List<SimpleCRLEntry> entries = Util.newList();
        List<CertificateSerial> serials =  
            this.certificateSerialCurator.retrieveTobeCollectedSerials();
        for (CertificateSerial cs : serials) {
            entries.add(new SimpleCRLEntry(cs.getSerial(),
                   new Date()));
            cs.setCollected(true);
        }
        if (log.isTraceEnabled()) {
            log.trace("Added #" + serials.size() + " new entries to the CRL");
        }
        if (log.isTraceEnabled()) {
            StringBuilder builder = new StringBuilder("[ ");
            for (CertificateSerial cs : serials) {
                builder.append(cs.getSerial()).append(", ");
            }
            builder.append(" ]");
            log.trace("Newly added serials = " + builder.toString());
        }
        this.certificateSerialCurator.saveOrUpdateAll(serials);
        if (log.isDebugEnabled()) {
            log.debug("Total number of serials retrieved from db: #" +
                entries.size());
        }
        
        return entries;
    }

    /**
     * Removes the expired serials.
     * 
     * @param revokedEntries the revoked entries
     * @return the set
     */
    protected Set<? extends X509CRLEntry> removeExpiredSerials(
        Set<? extends X509CRLEntry> revokedEntries) {
        if (revokedEntries == null || revokedEntries.size() == 0) {
            return Util.newSet();
        }
        Map<BigInteger, X509CRLEntry> map = newMap();
        for (X509CRLEntry entry : revokedEntries) {
            map.put(entry.getSerialNumber(), entry);
        }
        for (CertificateSerial cs : this.certificateSerialCurator
            .getExpiredSerials()) {
            X509CRLEntry entry = map.get(cs.getSerial());
            if (entry != null) {
                revokedEntries.remove(entry);
                if (log.isTraceEnabled()) {
                    log.trace("Serial #" + cs.getId() +
                        " has expired. Removing it from CRL");
                }
            }
        }
        return revokedEntries;
    }


    /**
     * Gets the cRL number.
     * 
     * @param x509crl the x509crl
     * @return the cRL number
     */
    protected BigInteger getCRLNumber(X509CRL x509crl) {
        if (x509crl == null) {
            return BigInteger.ZERO; 
        }
        return new BigInteger(getValue(x509crl, X509Extensions.CRLNumber.getId()));
    }
    
    /**
     * To simple crl entries.
     * 
     * @param entries the entries
     * @return the list
     */
    private List<SimpleCRLEntry> toSimpleCRLEntries(
        Set<? extends X509CRLEntry> entries) {
        List<SimpleCRLEntry> crlEntries = newList();
        for (X509CRLEntry entry : entries) {
            crlEntries.add(new SimpleCRLEntry(entry.getSerialNumber(), entry
                .getRevocationDate()));
        }
        return crlEntries;
    }
    
}
