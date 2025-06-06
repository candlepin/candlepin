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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.exceptions.BadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceCreationLiberalNameRules extends ConsumerResourceCreationTest {

    public DevConfig initConfig() {
        DevConfig config = TestConfig.defaults();

        config.setProperty(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN, ".+");
        config.setProperty(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN, ".+");
        config.setProperty(ConfigProperties.USE_SYSTEM_UUID_FOR_MATCHING, "true");

        return config;
    }

    // These overridden tests should receive an error with the default regex (from superclass),
    // but should be okay here

    @Test
    public void allowNameThatContainsMultibyteKorean() {
        assertNotNull(createConsumer("서브스크립션 "));
    }

    @Test
    public void allowNameThatContainsMultibyteOriya() {
        assertNotNull(createConsumer("ପରିବେଶ"));
    }

    @Test
    public void allowNameThatContainsBadCharacter() {
        createConsumer("bar$%camp");
    }

    @Test
    public void allowNameThatContainsWhitespace() {
        createConsumer("abc123 456");
    }

    @Test
    public void allowNameThatStartsWithBadCharacter() {
        createConsumer("<foo");
    }

    /*
     * This is a special case. We should never allow a name starting with pound,
     * regardless of the regex config.
     */
    @Test
    @Override
    public void disallowNameThatStartsWithPound() {
        assertThrows(BadRequestException.class,
            () -> createConsumer("#pound"));
    }

    @Test
    public void allowNameThatContainsJustAboutEverything() {
        createConsumer("0123456789-=~!@#$%^&*()_+{}[]|\\:;\"'<>,.?/");
    }

    @Test
    public void allowNameThatContainsRockDots() {
        createConsumer("áàéíñó€áâæÀÁåàüÄÆùúÆÔÓû");
    }

    @Test
    public void allowNameThatContainsFunUnicode() {
        // this is roughly: skull and crossbones,
        // snowman, neigher less than nor greater than,
        // intterobang
        createConsumer("\u2620 \u2603 \u2278 \u203D");
    }
}
