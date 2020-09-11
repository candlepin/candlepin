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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;



/**
 * ConsumerResourceCreationLiberalNameRules
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceCreationLiberalNameRules extends ConsumerResourceCreationTest {

    private static class ConfigForTesting extends MapConfiguration {
        @SuppressWarnings("serial")
        ConfigForTesting() {
            super(new HashMap<String, String>() {
                {
                    this.put(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN, ".+");
                    this.put(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN, ".+");
                }
            });
        }
    }
    public Configuration initConfig() {
        Configuration config = new ConfigForTesting();
        config.setProperty(ConfigProperties.USE_SYSTEM_UUID_FOR_MATCHING, "true");
        return config;
    }

    @Test
    public void containsMultibyteKorean() {
        assertNotNull(createConsumer("서브스크립션 "));
    }

    @Test
    public void containsMultibyteOriya() {
        assertNotNull(createConsumer("ପରିବେଶ"));
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
