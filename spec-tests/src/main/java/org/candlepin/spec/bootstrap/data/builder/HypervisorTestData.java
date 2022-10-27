/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class HypervisorTestData {
    private String expectedHostHypervisorId = StringUtil.random("host");
    private String expectedHostName = StringUtil.random("name");
    private String guest1VirtUuid = StringUtil.random("uuid");
    private String guest2VirtUuid = StringUtil.random("uuid");
    private List<String> expectedGuestIds = List.of(guest1VirtUuid, guest2VirtUuid);

    public String getExpectedHostHypervisorId() {
        return this.expectedHostHypervisorId;
    }

    public String getExpectedHostName() {
        return this.expectedHostName;
    }

    public String getGuest1VirtUuid() {
        return this.guest1VirtUuid;
    }

    public String getGuest2VirtUuid() {
        return this.guest2VirtUuid;
    }

    public List<String> getExpectedGuestIds() {
        return this.expectedGuestIds;
    }

    public List<GuestIdDTO>  getGuestIdDTOs() {
        List<GuestIdDTO> result = new ArrayList<>();
        for (String id : expectedGuestIds) {
            result.add(new GuestIdDTO().guestId(id));
        }
        return result;
    }
}
