/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.SimpleDTOFactory;
import org.candlepin.dto.api.APIDTOFactory;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.UpstreamConsumer;



/**
 * The APIv1DTOFactory is a DTOFactory pre-configured with the translators for creating DTOs
 * compatible with the API v1 specification.
 */
public class APIv1DTOFactory extends SimpleDTOFactory implements APIDTOFactory {

    public APIv1DTOFactory() {
        this.registerTranslator(Certificate.class, new CertificateTranslator());
        this.registerTranslator(CertificateSerial.class, new CertificateSerialTranslator());
        this.registerTranslator(ConsumerType.class, new ConsumerTypeTranslator());
        this.registerTranslator(Owner.class, new OwnerTranslator());
        this.registerTranslator(UpstreamConsumer.class, new UpstreamConsumerTranslator());
    }

}
