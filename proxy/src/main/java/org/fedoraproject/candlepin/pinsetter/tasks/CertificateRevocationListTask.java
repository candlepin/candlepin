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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.fedoraproject.candlepin.util.Util.getValue;
import static org.fedoraproject.candlepin.util.Util.newList;
import static org.fedoraproject.candlepin.util.Util.newMap;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.util.Util;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;

/**
 * CertificateRevocationListTask
 */
public class CertificateRevocationListTask implements Job {
    public static final String DEFAULT_SCHEDULE = "*/1 * * * * ?";
    private EntitlementCertificateCurator entCertCurator;

    private CertificateSerialCurator certificateSerialCurator;
    /**
     * @param entCertCurator the entCertCurator to set
     */
    public void setEntCertCurator(EntitlementCertificateCurator entCertCurator) {
        this.entCertCurator = entCertCurator;
    }

    @Inject
    public CertificateRevocationListTask(
        EntitlementCertificateCurator entCertCurator) {
        this.entCertCurator = entCertCurator;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        List<EntitlementCertificate> entCerts = entCertCurator.listAll();
        System.out.println("crl task ran: " + new Date().toString());
    }

    public CertificateSerialCurator getCertificateSerialCurator() {
        return certificateSerialCurator;
    }
    
    

    public void setCertificateSerialCurator(
        CertificateSerialCurator certificateSerialCurator) {
        this.certificateSerialCurator = certificateSerialCurator;
    }
    
    
    private static class SimpleCRLEntry{
        private BigInteger serialNumber;
        private Date revocationDate;
        /**
         * @param serialNumber
         * @param revocationDate
         */
        private SimpleCRLEntry(BigInteger serialNumber, Date revocationDate) {
            super();
            this.serialNumber = serialNumber;
            this.revocationDate = revocationDate;
        }
    }

    protected X509CRL generateCRL(Iterator<SimpleCRLEntry> entries,
        X509Certificate caCert, PrivateKey key, String principal, BigInteger crlNumber) {
        try {
            X509V2CRLGenerator generator = new X509V2CRLGenerator();
            generator.setIssuerDN(new X500Principal(principal));
            generator.setThisUpdate(new Date());
            //add all the crl entries.
            while (entries.hasNext()) {
                SimpleCRLEntry entry = entries.next();
                generator.addCRLEntry(entry.serialNumber, entry.revocationDate,
                    CRLReason.privilegeWithdrawn);
            }
            generator.addExtension(X509Extensions.AuthorityKeyIdentifier,
                false, new AuthorityKeyIdentifierStructure(caCert));
            generator.addExtension(X509Extensions.CRLNumber, false,
                new CRLNumber(crlNumber));
            return generator.generate(key);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected List<SimpleCRLEntry> getNewSerialsToAppendAndSetThemConsumed() {
        List<SimpleCRLEntry> entries = Util.newList();
        List<CertificateSerial> serials =  
            this.certificateSerialCurator.retrieveTobeCollectedSerials();
        for (CertificateSerial cs : serials) {
            entries.add(new SimpleCRLEntry(cs.getSerial(),
                    cs.getExpiration()));
            cs.setCollected(true);
        }
        this.certificateSerialCurator.saveOrUpdateAll(serials);
        return entries;
    }

    protected Set<? extends X509CRLEntry> removeExpiredSerials(
        Set<? extends X509CRLEntry> revokedEntries) {
        if (revokedEntries == null || revokedEntries.size() == 0) {
            return revokedEntries;
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
            }
        }
        return revokedEntries;
    }

    protected X509CRL updateCRL(X509CRL x509crl, X509Certificate caCert,
        PrivateKey key, String principal) {
        BigInteger no = getCRLNumber(x509crl);
        List<SimpleCRLEntry> crlEntries = newList();
        if (x509crl != null) {
            crlEntries = this.toSimpleCRLEntries(removeExpiredSerials(x509crl
                .getRevokedCertificates()));
        }
        crlEntries.addAll(getNewSerialsToAppendAndSetThemConsumed());
        this.certificateSerialCurator.deleteExpiredSerials();
        return this.generateCRL(crlEntries.iterator(), caCert, key, principal, no
            .add(BigInteger.ONE));
    }

    /**
     * @param x509crl
     * @return
     */
    private BigInteger getCRLNumber(X509CRL x509crl) {
        if (x509crl == null) {
            return BigInteger.ONE;
        }
        return new BigInteger(getValue(x509crl, "2.5.29.20"));
    }
    
    protected List<SimpleCRLEntry> toSimpleCRLEntries(
        Set<? extends X509CRLEntry> entries) {
        List<SimpleCRLEntry> crlEntries = newList();
        for (X509CRLEntry entry : entries) {
            crlEntries.add(new SimpleCRLEntry(entry.getSerialNumber(), entry
                .getRevocationDate()));
        }
        return crlEntries;
    }
    
    public void updateCRL(InputStream in, X509Certificate caCert,
        PrivateKey key, String principal, OutputStream out) {
        try {
            X509CRL x509crl = null;
            if (in != null) {
                x509crl = (X509CRL) CertificateFactory.getInstance("X.509")
                    .generateCRL(in);
            }
            x509crl = updateCRL(x509crl, caCert, key, principal);
            new PEMWriter(new OutputStreamWriter(out)).writeObject(x509crl);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }   
    }
    
}
