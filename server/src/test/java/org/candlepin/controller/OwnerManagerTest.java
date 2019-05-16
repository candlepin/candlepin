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
package org.candlepin.controller;

import static org.mockito.Mockito.*;

import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * RefresherTest
 */
@RunWith(MockitoJUnitRunner.class)
public class OwnerManagerTest {
    private OwnerManager ownerManager;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ActivationKeyCurator activationKeyCurator;
    @Mock
    private EnvironmentCurator envCurator;
    @Mock
    private ExporterMetadataCurator exportCurator;
    @Mock
    private ImportRecordCurator importRecordCurator;
    @Mock
    private PermissionBlueprintCurator permissionCurator;
    @Mock
    private OwnerProductCurator ownerProductCurator;
    @Mock
    private ProductManager productManager;
    @Mock
    private OwnerContentCurator ownerContentCurator;
    @Mock
    private ContentManager contentManager;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private ContentAccessCertServiceAdapter contentAccessCertService;
    @Mock
    private ContentAccessCertificateCurator contentAccessCertCurator;
    @Mock
    private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    @Mock
    private UeberCertificateCurator uberCertificateCurator;
    @Mock
    private OwnerServiceAdapter ownerServiceAdapter;

    @Before
    public void setUp() {
        ownerManager = new OwnerManager(consumerCurator, activationKeyCurator, envCurator,
            exportCurator, importRecordCurator, permissionCurator, ownerProductCurator, productManager,
            ownerContentCurator, contentManager, ownerCurator, contentAccessCertService,
            contentAccessCertCurator, ownerEnvContentAccessCurator, uberCertificateCurator,
            ownerServiceAdapter);
    }

    @Test
    public void testContentAccessSetEmpty() {
        Owner owner = new Owner();
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn("");
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn("");
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
        Assert.assertEquals(owner.getContentAccessModeList(),
            ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
        Assert.assertEquals(owner.getContentAccessMode(),
            ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
    }

    @Test
    public void testContentAccessSetNull() {
        Owner owner = new Owner();
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn(null);
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn(null);
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
        Assert.assertEquals(owner.getContentAccessModeList(),
            ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
        Assert.assertEquals(owner.getContentAccessMode(),
            ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE);
    }

    @Test(expected = IllegalStateException.class)
    public void testContentAccessModeNotOnList() {
        Owner owner = new Owner("test_owner", "test_owner");
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn("one,two");
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn("three");
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testContentAccessModeBlankSelection() {
        Owner owner = new Owner("test_owner", "test_owner");
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn("one,two");
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn("");
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testContentAccessModeNullSelection() {
        Owner owner = new Owner("test_owner", "test_owner");
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn("one,two");
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn(null);
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testContentAccessModeNoList() {
        Owner owner = new Owner();
        when(ownerCurator.lockAndLoad(eq(owner))).thenReturn(owner);
        when(ownerServiceAdapter.getContentAccessModeList(eq(owner.getKey()))).thenReturn("");
        when(ownerServiceAdapter.getContentAccessMode(eq(owner.getKey()))).thenReturn("three");
        ownerManager.refreshContentAccessMode(ownerServiceAdapter, owner);
    }
}
