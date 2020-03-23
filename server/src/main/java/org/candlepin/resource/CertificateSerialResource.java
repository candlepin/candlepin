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
package org.candlepin.resource;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;

import com.google.inject.Inject;



/**
 * CertificateSerialResource
 */
public class CertificateSerialResource implements SerialsApi {
    private CertificateSerialCurator certificateSerialCurator;
    private ModelTranslator translator;

    @Inject
    public CertificateSerialResource(CertificateSerialCurator certificateSerialCurator,
        ModelTranslator translator) {

        this.certificateSerialCurator = certificateSerialCurator;
        this.translator = translator;
    }

    @Override
    public CertificateSerialDTO getCertificateSerial(Long serialId) {
        CertificateSerial serial = this.certificateSerialCurator.get(serialId);
        return this.translator.translate(serial, CertificateSerialDTO.class);
    }

    @Override
    public CandlepinQuery<CertificateSerialDTO> getCertificateSerials() {
        CandlepinQuery<CertificateSerial> query = this.certificateSerialCurator.listAll();
        return this.translator.translateQuery(query, CertificateSerialDTO.class);
    }
}
