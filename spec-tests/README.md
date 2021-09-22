# Candlepin - Java base spec tests
Java spec tests for candlepin. 
  
# Getting Started
To run spec tests use the following commnand.
```
./gradlew clean spec
```
By default it expects candlepin running on **localhost:8443**. Defaults can be changed in **settings.properties** located in test resources. You can also override separate defaults by passing them as **-Dsome.key=value** to gradle

> Tests can be run from IDEA or any other IDE as well
