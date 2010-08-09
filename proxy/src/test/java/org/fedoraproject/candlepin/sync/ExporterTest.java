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
package org.fedoraproject.candlepin.sync;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.ExporterMetadataCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import org.junit.Test;

import java.io.File;


/**
 * ExporterTest
 */
public class ExporterTest {

    @Test
    public void getExport() throws ExportCreationException {
        ConsumerTypeCurator ctc = mock(ConsumerTypeCurator.class);
        MetaExporter me = new MetaExporter();
        ConsumerExporter ce = new ConsumerExporter();
        ConsumerTypeExporter cte = new ConsumerTypeExporter();
        RulesCurator rc = mock(RulesCurator.class);
        RulesExporter re = new RulesExporter(rc);
        EntitlementCertExporter ece = new EntitlementCertExporter();
        EntitlementCertServiceAdapter ecsa = mock(EntitlementCertServiceAdapter.class);
        ProductExporter pe = new ProductExporter();
        ProductServiceAdapter psa = mock(ProductServiceAdapter.class);
        ProductCertExporter pce = new ProductCertExporter();
        EntitlementCurator ec = mock(EntitlementCurator.class);
        EntitlementExporter ee = new EntitlementExporter();
        PKIUtility pki = mock(PKIUtility.class);
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, emc);
        Consumer consumer = mock(Consumer.class);
        File export = e.getExport(consumer);
        assertNotNull(export);
    }
}
