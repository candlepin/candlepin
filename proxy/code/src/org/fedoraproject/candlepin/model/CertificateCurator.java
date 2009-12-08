package org.fedoraproject.candlepin.model;

public class CertificateCurator extends AbstractHibernateCurator<Certificate> {

    protected CertificateCurator() {
        super(Certificate.class);
    }
}
