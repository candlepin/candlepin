/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.resource.server.v1.CertificateSerialApi;

import com.google.inject.persist.Transactional;

import javax.inject.Inject;



/**
 * CertificateSerialResource
 */
public class CertificateSerialResource implements CertificateSerialApi {
    private CertificateSerialCurator certificateSerialCurator;
    private ModelTranslator translator;

    @Inject
    public CertificateSerialResource(CertificateSerialCurator certificateSerialCurator,
        ModelTranslator translator) {

        this.certificateSerialCurator = certificateSerialCurator;
        this.translator = translator;
    }

    @Override
    @Transactional
    public CertificateSerialDTO getCertificateSerial(Long serialId) {
        CertificateSerial serial = this.certificateSerialCurator.get(serialId);
        return this.translator.translate(serial, CertificateSerialDTO.class);
    }
}
