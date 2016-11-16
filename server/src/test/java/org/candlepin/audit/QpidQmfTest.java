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
package org.candlepin.audit;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

public class QpidQmfTest {

    @Ignore("This test requires running qpid broker and is useful only for development")
    @Test
    public void liveTest() throws URISyntaxException, JMSException, InterruptedException {
        Configuration config = new CandlepinCommonTestConfig();

        config.setProperty("trust_store", "/etc/candlepin/certs/amqp/candlepin.truststore");
        config.setProperty("trust_store_password", "password");
        config.setProperty("key_store", "/etc/candlepin/certs/amqp/candlepin.jks");
        config.setProperty("key_store_password", "password");
        config.setProperty("retries", "0");
        config.setProperty("connectdelay", "1");
        QpidConnection connection = new QpidConnection(new QpidConfigBuilder(config));
        QpidQmf qmf = new QpidQmf(connection, config);
        System.out.println(qmf.getStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void extractStringNull() throws JMSException {
        QpidQmf.extractValue(null, "_values");
    }


    @Test
    public void extractStringWrongValueTypeTest() throws JMSException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("key", "value");

        try {
            QpidQmf.extractValue(map, "key");
        }
        catch (Exception e) {
            Assert.assertTrue(e.toString(), e.getMessage().contains("Expected the value to be byte[]"));
            return;
        }
        Assert.fail("Expected exception about value type");
    }

    @Test
    public void extractStringTooDeepTest() throws JMSException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("key", "value");

        try {
            QpidQmf.extractValue(map, "key", "key2");
        }
        catch (Exception e) {
            Assert.assertTrue(e.toString(), e.getMessage().contains("object under key key2 is not a map!"));
            return;
        }
        Assert.fail("Expected exception about value type");
    }

    @Test
    public void extractStringWrongKeyTest() throws JMSException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("key", "value");

        try {
            QpidQmf.extractValue(map, "key123");
        }
        catch (Exception e) {
            Assert.assertTrue(e.toString(), e.getMessage()
                .contains("The extracted value at key key123 was null"));
            return;
        }
        Assert.fail("Expected exception about value type");
    }

    @Test
    public void extractStringTest() throws JMSException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("key", "value".getBytes());

        Assert.assertEquals("value", QpidQmf.extractValue(map, "key"));
    }


    @Test
    public void extractStringTwoLevelsTest() throws JMSException {
        Map<String, Object> l2 = new HashMap<String, Object>();
        l2.put("key", "value".getBytes());
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("keyl1", l2);

        Assert.assertEquals("value", QpidQmf.extractValue(map, "keyl1", "key"));
    }
}
