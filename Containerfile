# Build candlepin war from source
FROM registry.redhat.io/ubi8/openjdk-11-runtime:latest AS build

ENV LANG en_US.UTF-8

USER root

COPY . .

# Update and install dependencies
RUN microdnf module enable pki-core && \
    microdnf install jss tomcatjss gettext wget initscripts openssl tar git && \
    microdnf clean all

RUN ./gradlew clean war && \
    (mkdir build/libs/candlepin && cd build/libs/candlepin; jar xf ../candlepin*.war) && \
    mv build/libs/candlepin/META-INF/context_tomcat8.xml build/libs/candlepin/META-INF/context.xml

# Resulting image contains the fully build Candlepin and none of the source.
FROM registry.redhat.io/ubi8/openjdk-11-runtime:latest

USER root:root

# Update and install dependencies
RUN microdnf module enable pki-core && \
    microdnf install jss tomcatjss openssl tar && \
    microdnf clean all

# Tomcat Setup
RUN mkdir /opt/tomcat /var/log/candlepin /var/lib/candlepin /etc/candlepin /etc/candlepin/certs; \
    curl --fail --retry 5 https://dlcdn.apache.org/tomcat/tomcat-8/v8.5.83/bin/apache-tomcat-8.5.83.tar.gz | tar -zx --strip-component 1 --dir /opt/tomcat; \
    rm -rf /opt/tomcat/logs; ln -s /var/log/tomcat /opt/tomcat/logs; \
    chown -R tomcat.tomcat /opt/tomcat /var/log/candlepin /var/log/tomcat /var/lib/candlepin /etc/candlepin; \
    find /opt/tomcat -type d -exec chmod o+rx {}  + ; \
    find /opt/tomcat -type f -exec chmod o+r {} + ; \
    find /opt/tomcat/bin -type f -name '*.sh' -exec chmod o+x {} + ; \
    chmod -R 775 /opt/tomcat/webapps; \
    rm -rf /opt/tomcat/webapps/ROOT; \
    chmod -R 775 /var/log/

# Copy exploded candlepin war to webapps dir
COPY --from=build --chown=tomcat:tomcat /home/jboss/build/libs/candlepin /opt/tomcat/webapps/candlepin

WORKDIR /opt/tomcat/bin
ENV HOME /opt/tomcat

USER tomcat

ENV JAVA_HOME=/usr/lib/jvm/jre-11-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-11-openjdk

# Expose ports for tomcat
EXPOSE 8080 8443

CMD ["/opt/tomcat/bin/catalina.sh", "run"]

