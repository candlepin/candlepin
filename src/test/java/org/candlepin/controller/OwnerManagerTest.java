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
package org.candlepin.controller;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;

import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the OwnerManager class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OwnerManagerTest {
    @Mock
    private PoolService poolService;
    @Mock
    private ConsumerCurator mockConsumerCurator;
    @Mock
    private ActivationKeyCurator mockActivationKeyCurator;
    @Mock
    private EnvironmentCurator mockEnvironmentCurator;
    @Mock
    private ExporterMetadataCurator mockExporterMetadataCurator;
    @Mock
    private ImportRecordCurator mockImportRecordCurator;
    @Mock
    private PermissionBlueprintCurator mockPermissionBlueprintCurator;
    @Mock
    private OwnerProductCurator mockOwnerProductCurator;
    @Mock
    private OwnerContentCurator mockOwnerContentCurator;
    @Mock
    private OwnerCurator mockOwnerCurator;
    @Mock
    private UeberCertificateCurator mockUeberCertificateCurator;

    private OwnerManager createManager() {
        return new OwnerManager(
            this.poolService, this.mockConsumerCurator, this.mockActivationKeyCurator,
            this.mockEnvironmentCurator, this.mockExporterMetadataCurator, this.mockImportRecordCurator,
            this.mockPermissionBlueprintCurator, this.mockOwnerProductCurator, this.mockOwnerContentCurator,
            this.mockOwnerCurator, this.mockUeberCertificateCurator);
    }

    @BeforeEach
    public void setup() {
        doAnswer(returnsFirstArg()).when(this.mockOwnerCurator).merge(any(Owner.class));
    }

    // TODO Test this class...

}
