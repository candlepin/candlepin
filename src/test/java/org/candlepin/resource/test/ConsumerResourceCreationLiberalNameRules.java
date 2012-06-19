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
package org.candlepin.resource.test;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.junit.Assert;
import org.junit.Test;
import java.util.HashMap;

/**
 * ConsumerResourceCreationLiberalNameRules
 */
public class ConsumerResourceCreationLiberalNameRules extends
    ConsumerResourceCreationTest {
    private Config config;

    private static class ConfigForTesting extends Config {
        @SuppressWarnings("serial")
        public ConfigForTesting() {
            super(new HashMap<String, String>() {
                {
                    this.put(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN, ".+");
                    this.put(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN, ".+");
                }
            });
        }
    }
    public Config initConfig() {
        Config config = new ConfigForTesting();
        return config;
    }

    @Test
    public void containsMultibyteKorean() {
        Assert.assertNotNull(createConsumer("서브스크립션 "));
    }

    @Test
    public void containsMultibyteOriya() {
        Assert.assertNotNull(createConsumer("ପରିବେଶ"));
    }
    // This fails with the normal regex, but should be okay here
    @Test
    public void containsBadCharacter() {
        createConsumer("bar$%camp");
    }

    @Test
    public void containsJustAboutEverything() {
        createConsumer("0123456789-=~!@#$%^&*()_+{}[]|\\:;\"'<>,.?/");
    }

    @Test
    public void containsRockDots() {
        createConsumer("áàéíñó€áâæÀÁåàüÄÆùúÆÔÓû");
    }

    @Test
    public void containsFunUnicode() {
        // this is roughly: skull and crossbones,
        // snowman, neigher less than nor greater than,
        // intterobang
        createConsumer("\u2620 \u2603 \u2278 \u203D");
    }
}
