package org.fedoraproject.candlepin;

import java.io.File;

public class ConfigDirectory {
    public static File directory() {
        return new File("/etc/candlepin");
    }
}
