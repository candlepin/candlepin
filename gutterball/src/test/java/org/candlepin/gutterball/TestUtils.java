package org.candlepin.gutterball;

import java.util.Date;
import java.util.Random;

import org.apache.commons.lang.RandomStringUtils;
import org.candlepin.gutterball.model.Event;

public class TestUtils {
	
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static int randomInt() {
        return Math.abs(RANDOM.nextInt());
    }
    
    public static String randomString(String prefix) {
    	return prefix + "-" + RandomStringUtils.randomAlphabetic(16);
    }
    
	public static Event createEvent(String type) {
		Event e = new Event();
		e.setId(randomString("ID"));
		e.setConsumerId(randomString("My Test Consumer"));
		e.setType("CREATE");
		e.setMessageText("This is a message");
		e.setTimestamp(new Date());
		return e;
	}

}
