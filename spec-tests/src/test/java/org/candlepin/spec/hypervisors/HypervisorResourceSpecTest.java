package org.candlepin.spec.hypervisors;

import org.candlepin.spec.bootstrap.client.SpecTest;
import org.junit.jupiter.api.Test;

@SpecTest
public class HypervisorResourceSpecTest {

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithUnknownOwner() {

    }

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithInvalidOwnerKey() {

    }

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithNullBody() {

    }

    // TODO: Validate

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithNullConsumerUuid() {

    }

    // TODO: Validate

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithUnknownConsumerUuid() {

    }

    @Test
    public void shouldNotRetrieveHypervisorsAndGuests() {

    }

    // TODO finish: The new Candlepin endpoint covers virt.uuid case insensitivity (in case hypervisor has reported it with different case than the guest).

    @Test
    public void shouldRetrieveHypervisorsAndGuestsWithUuidCaseDifferences() {

    }

    //TODO finish: he new Candlepin endpoint covers difference in virt.uuid endianness (in case hypervisor has reported it with different endianness than the guest).

    @Test
    public void shouldRetrieveHypervisorsAndGuestsWithUuidEndiannessDifferences() {

    }

}
