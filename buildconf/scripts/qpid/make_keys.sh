#!/bin/bash
# based on http://rajith.2rlabs.com/2010/03/01/apache-qpid-securing-connections-with-ssl/

# certificate information
COMPANY="O=CanadianTenPin,ST=North Carolina,C=US"
CA_NAME="CanadianTenPinCA"
CA_PASS="password"

SERVER_NAME="localhost"
SERVER_PASS="password"

JAVA_TRUSTPASS="password"
JAVA_KEYPASS="password"
KEYSTORE=keystore
TRUSTSTORE=truststore

CERT_LOC=keys
mkdir $CERT_LOC
cd $CERT_LOC

# prep for creating certificates
rm -rf CA_db
mkdir CA_db

echo "y" > ca_input
echo "0" >> ca_input
echo "n" >> ca_input

echo $CA_PASS > CA_db/pfile
certutil -N -d CA_db -f CA_db/pfile
dd bs=256 count=1 if=/dev/urandom of=noise
cat ca_input | certutil -S -d CA_db -n "$CA_NAME" -s "CN=$CA_NAME,$COMPANY" -t "CT,," -x -2 -f CA_db/pfile -z noise
rm noise ca_input

certutil -L -d CA_db -n "$CA_NAME" -a -o CA_db/rootca.crt -f CA_db/pfile

rm -rf server_db
mkdir server_db
echo $SERVER_PASS > server_db/pfile
certutil -N -d server_db -f server_db/pfile

# import the CA certificate into the DB
certutil -A -d server_db -n "$CA_NAME" -t "TC,," -a -i CA_db/rootca.crt -f server_db/pfile
dd bs=256 count=1 if=/dev/urandom of=noise
certutil -R -d server_db -s "CN=$SERVER_NAME,$COMPANY" -a -o server_db/server.req -z noise -f server_db/pfile
rm noise

echo "0" > sign_serv
echo "9" >> sign_serv
echo "n" >> sign_serv
echo "n" >> sign_serv
echo "-1" >> sign_serv
echo "n" >> sign_serv

echo "sleeping for entropy"
sleep 2

cat sign_serv | certutil -C -d CA_db -c "$CA_NAME" -a -i server_db/server.req -o server_db/server.crt -2 -6 -f CA_db/pfile
certutil -A -d server_db -n $SERVER_NAME -a -i server_db/server.crt -t ",," -f server_db/pfile

rm -f $TRUSTSTORE $KEYSTORE sign_serv

# import the CA certificate in to the trust store
keytool -import -v -keystore $TRUSTSTORE -storepass $JAVA_TRUSTPASS -alias $CA_NAME -file CA_db/rootca.crt -noprompt

# import the CA certificate into the keystore (for client authentication)
keytool -import -v -keystore $KEYSTORE -storepass $JAVA_KEYPASS -alias $CA_NAME -file CA_db/rootca.crt -noprompt

# generate keys for the client certificate
keytool -genkey -alias amqp-client -keyalg RSA -sigalg SHA256withRSA -validity 356 -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -dname "CN=java-client,$COMPANY"
# create certificate request
keytool -certreq -alias amqp-client -sigalg SHA256withRSA -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -v -file java-client.req

# sign the certificate request using the CA certificate
echo "1" > sign_client
echo "9" >> sign_client
echo "n" >> sign_client
echo "n" >> sign_client
echo "-1" >> sign_client
echo "n" >> sign_client

cat sign_client | certutil -C -d CA_db -c "$CA_NAME" -a -i java-client.req -o java-client.crt -2 -6 -f CA_db/pfile
rm sign_client

# import the certicate into the keystore
keytool -import -v -alias amqp-client -keystore $KEYSTORE -storepass $JAVA_KEYPASS -file java-client.crt


# copy the keystore to the right place
sudo mkdir -p /etc/canadianTenPin/certs/amqp/
sudo cp keystore /etc/canadianTenPin/certs/amqp/
sudo cp truststore /etc/canadianTenPin/certs/amqp/
sudo chown -R tomcat:tomcat /etc/canadianTenPin/certs/amqp/
