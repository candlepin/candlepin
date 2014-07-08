#!/bin/bash
#CA_NAME="CanadianTenPinCA"
CA_NAME="ca"
JAVA_TRUSTPASS="password"
JAVA_KEYPASS="password"
KEYSTORE=keystore
TRUSTSTORE=truststore
CA_CERT='/etc/pki/pulp/qpid/ca.crt'
PWDFILE='CA_db/pfile'
COMPANY="O=CanadianTenPin,ST=North Carolina,C=US"

rm -rf CA_db
mkdir CA_db

echo ""
echo "Please specify the CA certificate associated with your QPID broker."
echo ""
read -p "Enter a directory [$CA_CERT]:" ans
if [ "${#ans}" -gt 0 ]
then
  CA_CERT=$ans
fi
echo $CA_CERT

# prompt user for the DB password
echo ""
echo "Please enter a password for the NSS database.  Generated if not specified."
echo ""
read -sp "Enter a password:" ans
if [ "${#ans}" -gt 0 ]
then
  DB_PASSWORD=$ans
fi
echo ""
echo "Using password: [$DB_PASSWORD]"

#
# ========== PASSWORD ===========
#

# create the password file
echo "$DB_PASSWORD" > $PWDFILE

echo ""
echo "Password file created."

# based on
# http://rajith.2rlabs.com/2010/03/01/apache-qpid-securing-connections-with-ssl/


certutil -N -d CA_db -f $PWDFILE

## import the CA certificate into the DB
#certutil -A -d CA_db -n 'ca' -t 'TCu,Cu,Tuw' -a -i $CA_CERT
#echo "CA certificate: $CA_CERT, imported"

# import the CA certificate in to the trust store
keytool -import -v -keystore $TRUSTSTORE -storepass $JAVA_TRUSTPASS -alias $CA_NAME -file $CA_CERT -noprompt

# import the CA certificate into the keystore (for client authentication)
keytool -import -v -keystore $KEYSTORE -storepass $JAVA_KEYPASS -alias $CA_NAME -file $CA_CERT -noprompt

# generate keys for the client certificate
#keytool -genkey -alias amqp-client -keyalg RSA -sigalg MD5withRSA -validity 356 -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -dname "CN=java-client,$COMPANY"
keytool -genkey -alias amqp-client -keyalg RSA -sigalg SHA256withRSA -validity 356 -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -dname "CN=java-client,$COMPANY"

# create certificate request
#keytool -certreq -alias amqp-client -sigalg MD5withRSA -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -v -file java-client.req
keytool -certreq -alias amqp-client -sigalg SHA256withRSA -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -v -file java-client.req

# sign the certificate request using the CA certificate
echo "1" > sign_client
echo "9" >> sign_client
echo "n" >> sign_client
echo "n" >> sign_client
echo "-1" >> sign_client
echo "n" >> sign_client

cat sign_client | certutil -C -d CA_db -c "$CA_NAME" -a -i java-client.req -o java-client.crt -2 -6 -f $PWDFILE
rm sign_client


# import the certicate into the keystore
keytool -import -v -alias amqp-client -keystore $KEYSTORE -storepass $JAVA_KEYPASS -file java-client.crt

# copy the keystore to the right place
sudo mkdir -p /etc/canadianTenPin/certs/amqp/
sudo cp keystore /etc/canadianTenPin/certs/amqp/
sudo cp truststore /etc/canadianTenPin/certs/amqp/
sudo chown -R tomcat:tomcat /etc/canadianTenPin/certs/amqp/
