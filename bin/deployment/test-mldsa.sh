#!/bin/bash
# Test if ML-DSA KeyFactory is available with BouncyCastle

TOMCAT_LIB="/opt/tomcat/lib"

cat > /tmp/TestMLDSA.java <<'JAVAEOF'
import java.security.Security;
import java.security.Provider;
import java.security.KeyFactory;

public class TestMLDSA {
    public static void main(String[] args) {
        // Add BC providers
        try {
            Class<?> bcProvider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            Security.addProvider((Provider)bcProvider.newInstance());

            Class<?> bcpqcProvider = Class.forName("org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider");
            Security.addProvider((Provider)bcpqcProvider.newInstance());
        } catch (Exception e) {
            System.out.println("Error adding providers: " + e);
            e.printStackTrace();
        }

        // List all providers
        System.out.println("=== Security Providers ===");
        for (Provider p : Security.getProviders()) {
            System.out.println(p.getName() + " - " + p.getVersion());
        }

        // Try to get ML-DSA KeyFactory
        System.out.println("\n=== Testing ML-DSA KeyFactory ===");
        String[] names = {"ML-DSA", "MLDSA", "ML-DSA-65", "MLDSA65", "Dilithium3", "2.16.840.1.101.3.4.3.18"};

        for (String name : names) {
            try {
                KeyFactory kf = KeyFactory.getInstance(name);
                System.out.println("SUCCESS: " + name + " -> " + kf.getProvider().getName());
            } catch (Exception e) {
                System.out.println("FAILED: " + name + " -> " + e.getMessage());
            }
        }

        // List all KeyFactory algorithms from BCPQC
        System.out.println("\n=== BCPQC KeyFactory Algorithms ===");
        Provider bcpqc = Security.getProvider("BCPQC");
        if (bcpqc != null) {
            bcpqc.getServices().stream()
                .filter(s -> s.getType().equals("KeyFactory"))
                .forEach(s -> System.out.println("  " + s.getAlgorithm()));
        } else {
            System.out.println("BCPQC provider not found!");
        }
    }
}
JAVAEOF

echo "Compiling test..."
javac /tmp/TestMLDSA.java

echo ""
echo "Running test..."
java -cp /tmp:${TOMCAT_LIB}/* TestMLDSA

echo ""
echo "Cleaning up..."
rm -f /tmp/TestMLDSA.java /tmp/TestMLDSA.class
