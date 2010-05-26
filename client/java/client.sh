# ENV Variables
CANDLEPIN_HOME=`dirname $0`
REPO=$HOME/.m2/repository
cp_entries=( $CANDLEPIN_HOME/target/candlepin-client-0.4.jar $REPO/commons-cli/commons-cli/1.2/commons-cli-1.2.jar $REPO/commons-httpclient/commons-httpclient/3.1/commons-httpclient-3.1.jar $REPO/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar $REPO/org/jboss/resteasy/resteasy-jaxrs/1.2.1.GA/resteasy-jaxrs-1.2.1.GA.jar $REPO/org/jboss/resteasy/jaxrs-api/1.2.1.GA/jaxrs-api-1.2.1.GA.jar $REPO/org/slf4j/slf4j-api/1.5.8/slf4j-api-1.5.8.jar $REPO/org/slf4j/slf4j-simple/1.5.8/slf4j-simple-1.5.8.jar $REPO/commons-codec/commons-codec/1.4/commons-codec-1.4.jar $REPO/org/bouncycastle/bcprov-jdk16/1.44/bcprov-jdk16-1.44.jar $REPO/org/jboss/resteasy/resteasy-jaxb-provider/1.2.1.GA/resteasy-jaxb-provider-1.2.1.GA.jar $REPO/commons-lang/commons-lang/2.5/commons-lang-2.5.jar $REPO/org/codehaus/jackson/jackson-xc/1.5.2/jackson-xc-1.5.2.jar $REPO/org/codehaus/jackson/jackson-jaxrs/1.5.2/jackson-jaxrs-1.5.2.jar $REPO/org/codehaus/jackson/jackson-core-lgpl/1.5.2/jackson-core-lgpl-1.5.2.jar $REPO/org/codehaus/jackson/jackson-mapper-lgpl/1.5.2/jackson-mapper-lgpl-1.5.2.jar $REPO/org/jboss/netty/netty/3.1.5.GA/netty-3.1.5.GA.jar $REPO/org/jboss/resteasy/resteasy-jackson-provider/1.2.1.GA/resteasy-jackson-provider-1.2.1.GA.jar )

for entry in ${cp_entries[@]}
do
    CLASSPATH=$CLASSPATH:$entry
done
#echo $CLASSPATH
java -classpath $CLASSPATH org.fedoraproject.candlepin.client.CLIMain $*
