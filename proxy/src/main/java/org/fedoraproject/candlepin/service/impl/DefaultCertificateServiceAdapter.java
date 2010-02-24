package org.fedoraproject.candlepin.service.impl;

import java.security.cert.X509Certificate;
import java.util.Date;

import org.fedoraproject.candlepin.model.ClientCertificate;
import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.CertificateServiceAdapter;
import org.fedoraproject.candlepin.resource.cert.CertGenerator;

public class DefaultCertificateServiceAdapter implements
    CertificateServiceAdapter {
    
    @Override
    public ClientCertificateStatus generateEntitlementCert(Consumer consumer,
        Subscription sub, Product product, Date endDate) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ClientCertificateStatus generateIdentityCert(Consumer consumer) {
        // TODO Auto-generated method stub
        ClientCertificateStatus certStatus =  new ClientCertificateStatus();
        return certStatus;
    }

}
