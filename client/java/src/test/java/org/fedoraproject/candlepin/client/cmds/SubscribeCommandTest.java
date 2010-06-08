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
package org.fedoraproject.candlepin.client.cmds;

import static org.mockito.Matchers.*;
import org.apache.commons.cli.CommandLine;
import org.fedoraproject.candlepin.client.CandlepinClientFacade;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * SubscribeCommandTest
 * @author Ajay Kumar Nadathur Sreenivasan.
 */
@RunWith(MockitoJUnitRunner.class)
public class SubscribeCommandTest {

    @Mock private CommandLine commandLine;
    @Mock private CandlepinClientFacade facade;
    private SubscribeCommand cmd;

    @Before public void initialize() {
        cmd = new SubscribeCommand();
        cmd.setClient(facade);
        when(facade.isRegistered()).thenReturn(true);
    }

    @Test
    public void testSinglePoolInput() {
        stubGetOptionValues("p", "1");
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(1L, 1);
    }

    @Test
    public void testMultiplePoolsInput() {
        when(commandLine.getOptionValues(eq("p")))
            .thenReturn(new String[] {"1", "2", "4"});
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(1L, 1);
        verify(facade, times(1)).bindByPool(2L, 1);
        verify(facade, times(1)).bindByPool(4L, 1);
    }

    @Test
    public void testSinglePoolWithQuantityArgs() {
        stubGetOptionValues("p", "1");
        when(commandLine.getOptionValues(eq("q")))
            .thenReturn(new String[] {"10"});
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(1L, 10);

    }

    @Test
    public void testSinglePoolWithMultipleQuantityArgs() {
        when(commandLine.getOptionValues(eq("p"))).thenReturn(
            new String[]{ "123" });
        when(commandLine.getOptionValues(eq("q")))
            .thenReturn(new String[] {"10", "20", "30"});
        cmd.execute(commandLine, facade);
        verify(facade, atMost(1)).bindByPool(123L, 10);
    }

    @Test
    public void testAllSinglesWithNoQuantity() {
        stubGetOptionValues("p", "789");
        stubGetOptionValues("pr", "456");
        stubGetOptionValues("r", "abcd-efg-1234");
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(789L, 1);
        verify(facade, times(1)).bindByProductId("456", 1);
        verify(facade, times(1)).bindByRegNumber("abcd-efg-1234", 1);
    }

    @Test
    public void testAllSinglesWithQuantity() {
        stubGetOptionValues("p", "789");
        stubGetOptionValues("pr", "456");
        stubGetOptionValues("r", "abcd-efg-1234");
        stubGetOptionValues("q", "10, 20, 30");
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(789L, 10);
        verify(facade, times(1)).bindByProductId("456", 20);
        verify(facade, times(1)).bindByRegNumber("abcd-efg-1234", 30);
    }

    @Test
    public void testAllMultiplesWithQuantities() {
        stubGetOptionValues("p", "789, 123, 456");
        stubGetOptionValues("pr", "456,222,341");
        stubGetOptionValues("r", "abcd-efg-1234,qwerty-038763-123");
        stubGetOptionValues("q", "10, 20, 30, 50, 2, 3");
        cmd.execute(commandLine, facade);
        verify(facade, times(1)).bindByPool(789L, 10);
        verify(facade, times(1)).bindByPool(123L, 20);
        verify(facade, times(1)).bindByPool(456L, 30);

        verify(facade, times(1)).bindByProductId("456", 50);
        verify(facade, times(1)).bindByProductId("222", 2);
        verify(facade, times(1)).bindByProductId("341", 3);

        verify(facade, times(1)).bindByRegNumber("abcd-efg-1234", 1);
        verify(facade, times(1)).bindByRegNumber("qwerty-038763-123", 1);
    }

    private void stubGetOptionValues(String opt, String [] vals) {
        when(commandLine.getOptionValues(eq(opt)))
            .thenReturn(vals);
    }
    /**
     *
     */
    private void stubGetOptionValues(String opt, String value) {
        stubGetOptionValues(opt, value.split(","));
    }

}
