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
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateCurator;

import com.google.inject.Inject;

import java.math.BigInteger;
import java.util.stream.Stream;



/**
 * CertificateSerialResource
 */
public class CertificateSerialResource implements SerialsApi {
    private CertificateCurator certificateCurator;
    private ModelTranslator translator;

    @Inject
    public CertificateSerialResource(CertificateCurator certificateCurator, ModelTranslator translator) {
        this.certificateCurator = Objects.requireNonNull(certificateCurator);
        this.translator = Objects.requireNonNull(translator);
    }

    private BigInteger convertSerial(String serial) {
        try {
            return new BigInteger(serial);
        }
        catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid serial format"); // TODO: This needs to be translated
        }
    }

    @Override
    public CertificateSerialDTO getCertificateSerial(String serial) {
        BigInteger converted = this.convertSerial(serial);
        Certificate certificate = this.certificateCurator.getBySerial(converted);

        if (certificate == null) {
            throw new NotFoundException("no such serial: " + serial); // TODO: this needs to be translated
        }

        return this.translator.translate(certificate, CertificateSerialDTO.class);
    }

    @Override
    public Stream<CertificateSerialDTO> getCertificateSerials() {
        return this.certificateCurator.list()
            .stream()
            .map(this.translator.getStreamMapper(Certificate.class, CertificateSerialDTO.class));
    }
}
