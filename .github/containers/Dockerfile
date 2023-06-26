FROM quay.io/centos/centos:stream9 as builder

USER root

# Update and install dependencies
RUN dnf -y --setopt install_weak_deps=False update && \
    dnf -y --setopt install_weak_deps=False install java-17-openjdk-devel jss gettext && \
    dnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-17-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-17-openjdk

# Copy source code
COPY . ./candlepin
WORKDIR /candlepin

# Build the Candlpepin WAR file.
# Note: we need to includes -Phostedtest=true so that we can run spec tests
# using our hosted test endpoints.
RUN ./gradlew war -Phostedtest=true && \
    mkdir -p /app/build && \
    cp $(find ./build/libs -name 'candlepin*.war' | head -n 1) /app/build

WORKDIR /app/build
RUN jar xf $(find . -name 'candlepin*.war' | head -n 1); \
    sed -i 's/jss4.jar/jss.jar/g' ./META-INF/context.xml

FROM registry.access.redhat.com/ubi9-minimal:9.2-691
LABEL author="Josh Albrecht <jalbrech@redhat.com>"

USER root

# Update and install dependencies
RUN microdnf -y update && \
    microdnf -y update ca-certificates && \
    microdnf install -y java-17-openjdk-headless openssl jss initscripts wget tar && \
    microdnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-17-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-17-openjdk
ENV CATALINA_OPTS=-Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts

# Tomcat Setup
RUN wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.76/bin/apache-tomcat-9.0.76.tar.gz; \
    tar xzf apache-tomcat-9.0.76.tar.gz; \
    mkdir /opt/tomcat; \
    mv apache-tomcat-9.0.76/* /opt/tomcat/; \
    rm apache-tomcat-9.0.76.tar.gz; \
    rm -R apache-tomcat-9.0.76; \
    mkdir -p /etc/candlepin/certs; \
    mkdir -p /var/cache/candlepin/sync; \
    groupadd -g 10000 tomcat; \
    useradd -g tomcat -u 10001 tomcat; \
    chown -R tomcat.tomcat /opt/tomcat; \
    chown -R tomcat.tomcat /var/log/; \
    chown -R tomcat.tomcat /var/lib/; \
    chown -R tomcat.tomcat /etc/candlepin/; \
    chown -R tomcat:tomcat /var/cache/; \
    chmod -R 775 /opt/tomcat/webapps; \
    chmod -R 775 /var/log/;

# Candlepin install
COPY --from=builder /app/build /opt/tomcat/webapps/candlepin

# Setup certificate and key
WORKDIR /etc/candlepin/certs
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh --trust --cert_out ./candlepin-ca.crt --key_out ./candlepin-ca.key --hostname candlepin; \
    rm gen_certs.sh;

COPY ./.github/containers/server.xml /opt/tomcat/conf

WORKDIR /opt/tomcat/bin

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

CMD ["/opt/tomcat/bin/catalina.sh", "run"]
