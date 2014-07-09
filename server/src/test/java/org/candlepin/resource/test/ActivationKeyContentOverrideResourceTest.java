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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Owner;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.resource.ActivationKeyContentOverrideResource;
import org.candlepin.util.ContentOverrideValidator;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;

/**
 * ActivationKeyContentOverrideResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ActivationKeyContentOverrideResourceTest {

    @Mock
    private Owner owner;
    @Mock
    private ActivationKeyContentOverrideCurator activationKeyContentOverrideCurator;

    private ActivationKeyContentOverrideResource akcor;
    @Mock
    private ActivationKeyCurator akc;
    @Mock
    private ActivationKey key;
    @Mock
    private UriInfo context;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;

    @Mock
    private Principal principal;

    @Mock
    private I18n i18n;

    @Before
    public void setUp() throws URISyntaxException {
        key = new ActivationKey("actkey", owner);
        key.setId("keyid");
        MultivaluedMap<String, String> mvm = new MultivaluedMapImpl<String, String>();
        mvm.add("activation_key_id", key.getId());
        when(context.getPathParameters()).thenReturn(mvm);
        akcor = new ActivationKeyContentOverrideResource(
            activationKeyContentOverrideCurator, akc, contentOverrideValidator, i18n);
        when(akc.verifyAndLookupKey(eq(key.getId()))).thenReturn(key);
        when(principal.canAccess(any(Object.class), any(SubResource.class),
            any(Access.class))).thenReturn(true);
    }

    @Test
    public void testActivationKeyGetOverrides() {
        List<ActivationKeyContentOverride> overrides =
            new LinkedList<ActivationKeyContentOverride>();
        ActivationKeyContentOverride override =
            new ActivationKeyContentOverride(key, "label", "name", "value");
        overrides.add(override);

        when(activationKeyContentOverrideCurator.getList(eq(key))).thenReturn(overrides);
        List<ActivationKeyContentOverride> res =
            akcor.getContentOverrideList(context, principal);

        assertEquals(overrides, res);
    }

    @Test
    public void testActivationKeyDeleteOverride() {
        ActivationKeyContentOverride override =
            new ActivationKeyContentOverride(key, "label", "name", "test");

        List<ContentOverride> toDelete = new LinkedList<ContentOverride>();
        toDelete.add(override);
        akcor.deleteContentOverrides(context, principal, toDelete);
        verify(activationKeyContentOverrideCurator, Mockito.times(1))
            .removeByName(eq(key), eq(override.getContentLabel()), eq(override.getName()));
    }

    @Test
    public void testActivationKeyRemoveAllOverrides() {
        List<ContentOverride> toDelete = new LinkedList<ContentOverride>();
        akcor.deleteContentOverrides(context, principal, toDelete);
        verify(activationKeyContentOverrideCurator, Mockito.times(1))
            .removeByParent(eq(key));
    }

    @Test
    public void testActivationKeyAddOverrides() {
        ActivationKeyContentOverride override =
            new ActivationKeyContentOverride(key, "somelabel", "test", "test");
        ActivationKeyContentOverride otherOverride =
            new ActivationKeyContentOverride(key, "somelabel1", "test", "test");

        List<ContentOverride> toAdd = new LinkedList<ContentOverride>();
        toAdd.add(override);
        toAdd.add(otherOverride);

        akcor.addContentOverrides(context, principal, toAdd);
        verify(activationKeyContentOverrideCurator, Mockito.times(1))
            .addOrUpdate(eq(key), eq(override));
        verify(activationKeyContentOverrideCurator, Mockito.times(1))
            .addOrUpdate(eq(key), eq(otherOverride));
    }

    @Test
    public void testActivationKeyOverrideValidationIsRun() {
        List<ContentOverride> newOverrides = new LinkedList<ContentOverride>();
        newOverrides.add(new ActivationKeyContentOverride(key, "x", "baseurl", "x"));
        akcor.addContentOverrides(context, principal, newOverrides);
        verify(contentOverrideValidator, Mockito.times(1)).validate(eq(newOverrides));
    }
}
