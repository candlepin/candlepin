FROM quay.io/centos/centos:stream8 as builder

USER root

# Update and install dependencies
RUN dnf -y --setopt install_weak_deps=False update && \
    dnf module enable -y pki-core pki-deps && \
    dnf -y --setopt install_weak_deps=False install java-11-openjdk-devel jss gettext && \
    dnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-11-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-11-openjdk

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
RUN jar xf $(find . -name 'candlepin*.war' | head -n 1)

FROM quay.io/centos/centos:stream8
LABEL author="Josh Albrecht <jalbrech@redhat.com>"

USER root

# Update and install dependencies
RUN dnf -y update && \
    dnf -y update ca-certificates && \
    dnf module enable -y pki-core pki-deps && \
    dnf install -y java-11-openjdk-headless openssl jss initscripts wget tar unzip && \
    dnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-11-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-11-openjdk
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
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh -H candlepin; \
    rm gen_certs.sh;

COPY ./.github/containers/server.xml /opt/tomcat/conf

RUN alternatives --install /usr/bin/java java /usr/lib/jvm/jre-11/bin/java 1
RUN alternatives --set java /usr/lib/jvm/jre-11/bin/java

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

COPY ./.github/containers/entrypoint.sh /

ENTRYPOINT ["/entrypoint.sh"]
