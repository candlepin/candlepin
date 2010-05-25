# ENV Variables
CANDLEPIN_HOME=`dirname $0`
REPO=$HOME/.m2/repository

#Classpath
CLASSPATH=$CANDLEPIN_HOME/target/classes:$CANDLEPIN_HOME/target/candlepin-client-0.0.4.jar
CLASSPATH=$CLASSPATH:$REPO/commons-cli/commons-cli/1.2/commons-cli-1.2.jar
CLASSPATH=$CLASSPATH:$REPO/commons-httpclient/commons-httpclient/3.1/commons-httpclient-3.1.jar
CLASSPATH=$CLASSPATH:$REPO/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar
CLASSPATH=$CLASSPATH:$REPO/org/jboss/resteasy/resteasy-jaxrs/1.2.1.GA/resteasy-jaxrs-1.2.1.GA.jar
CLASSPATH=$CLASSPATH:$REPO/org/jboss/resteasy/jaxrs-api/1.2.1.GA/jaxrs-api-1.2.1.GA.jar
CLASSPATH=$CLASSPATH:$REPO/org/slf4j/slf4j-api/1.5.8/slf4j-api-1.5.8.jar
CLASSPATH=$CLASSPATH:$REPO/org/slf4j/slf4j-simple/1.5.8/slf4j-simple-1.5.8.jar
CLASSPATH=$CLASSPATH:$REPO/commons-codec/commons-codec/1.4/commons-codec-1.4.jar
CLASSPATH=$CLASSPATH:$REPO/org/bouncycastle/bcprov-jdk16/1.44/bcprov-jdk16-1.44.jar
CLASSPATH=$CLASSPATH:$REPO/org/jboss/resteasy/resteasy-jaxb-provider/1.2.1.GA/resteasy-jaxb-provider-1.2.1.GA.jar
CLASSPATH=$CLASSPATH:$REPO/org/jboss/resteasy/resteasy-jettison-provider/1.2.1.GA/resteasy-jettison-provider-1.2.1.GA.jar
#CLASSPATH=$CLASSPATH:$REPO/org/codehaus/jackson/jackson-jaxrs/1.4.1/jackson-jaxrs-1.4.1.jar
#CLASSPATH=$CLASSPATH:$REPO/org/codehaus/jackson/jackson-mapper-lgpl/1.4.1/jackson-mapper-lgpl-1.4.1.jar
#CLASSPATH=$CLASSPATH:$REPO/org/codehaus/jackson/jackson-core-lgpl/1.4.1/jackson-core-lgpl-1.4.1.jar
CLASSPATH=$CLASSPATH:$REPO/org/codehaus/jettison/jettison/1.1/jettison-1.1.jar


java -classpath $CLASSPATH org.fedoraproject.candlepin.client.CLIMain $*
