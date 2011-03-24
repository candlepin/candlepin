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

import static org.fedoraproject.candlepin.util.Util.newList;
import static org.fedoraproject.candlepin.util.Util.newMap;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509CRLEntryWrapper;
import org.fedoraproject.candlepin.util.OIDUtil;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

// TODO:  Clean up these protected methods - most are protected only for unit testing!
/**
 * CRLGenerator
 */
public class CrlGenerator {

    private PKIUtility pkiUtility;
    private CertificateSerialCurator certificateSerialCurator;

    private static Logger log = Logger.getLogger(CrlGenerator.class);
    
    /**
     * Instantiates a new certificate revocation list task.
     * 
     * @param curator the curator
     * @param pkiUtility PKIUtility for crl creation
     */
    @Inject
    public CrlGenerator(CertificateSerialCurator curator, PKIUtility pkiUtility) {
        
        this.certificateSerialCurator = curator;
        this.pkiUtility = pkiUtility;
    }

    /**
     * Update crl.
     * 
     * @param x509crl the x509crl
     * @return the x509 crl
     */
    public X509CRL updateCRL(X509CRL x509crl) {
        List<X509CRLEntryWrapper> crlEntries = null;
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
        
        return pkiUtility.createX509CRL(crlEntries, no
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
     * Gets the new serials to append and set them consumed.
     * 
     * @return the new serials to append and set them consumed
     */
    protected List<X509CRLEntryWrapper> getNewSerialsToAppendAndSetThemConsumed() {
        List<X509CRLEntryWrapper> entries = Util.newList();
        List<CertificateSerial> serials =  
            this.certificateSerialCurator.retrieveTobeCollectedSerials();
        for (CertificateSerial cs : serials) {
            entries.add(new X509CRLEntryWrapper(cs.getSerial(),
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
        return new BigInteger(pkiUtility.decodeDERValue(x509crl.getExtensionValue(
            OIDUtil.CRL_NUMBER)));
    }
    
    /**
     * To simple crl entries.
     * 
     * @param entries the entries
     * @return the list
     */
    private List<X509CRLEntryWrapper> toSimpleCRLEntries(
        Set<? extends X509CRLEntry> entries) {
        List<X509CRLEntryWrapper> crlEntries = newList();
        for (X509CRLEntry entry : entries) {
            crlEntries.add(new X509CRLEntryWrapper(entry.getSerialNumber(), entry
                .getRevocationDate()));
        }
        return crlEntries;
    }
    
}
