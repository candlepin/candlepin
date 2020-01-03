# This container provides a reusable test environment for candlepin.
# The goals for it are be reusable as a quick environment for developers
# that can also be used as a Jenkins Node.

FROM fedora:31

ARG JENKINS_REMOTING_VERSION=3.35
ARG USER=jenkins
ARG UID=1000
ARG JAVA_VERSION=java-1.8.0
ARG RUBY_VERSION=2.5.3

ENV LANG en_US.UTF-8
ENV JAVA_HOME /usr/lib/jvm/${JAVA_VERSION}/
ENV RUBY_VERSION ${RUBY_VERSION}

RUN groupadd rvm \
  && useradd -c "Jenkins user" -d /home/${USER} -u ${UID} -m ${USER} -G rvm

# everything else should be for the candlepin testing env
RUN yum install --disablerepo=updates -y \
    gettext \
    git \
    jss \
    ${JAVA_VERSION}-openjdk \
    ${JAVA_VERSION}-openjdk-devel \
    openssl \
    && yum clean all

# This jar is used so this container is deployable as a jenkins node
RUN curl --create-dirs -sSLo /usr/share/jenkins/agent.jar https://repo.jenkins-ci.org/public/org/jenkins-ci/main/remoting/$JENKINS_REMOTING_VERSION/remoting-$JENKINS_REMOTING_VERSION.jar \
  && chmod 755 /usr/share/jenkins \
  && chmod 644 /usr/share/jenkins/agent.jar \
  && ln -sf /usr/share/jenkins/agent.jar /usr/share/jenkins/slave.jar

## RUBY setup
# rvm needs sudo
# RUN echo "${USER}  ALL=(ALL) NOPASSWD:ALL" | sudo tee /etc/sudoers.d/${USER}
RUN yum install -y procps-ng gcc-c++ patch readline readline-devel zlib zlib-devel \
        libyaml-devel libffi-devel openssl-devel make \
        bzip2 autoconf automake libtool bison sqlite-devel \
        && yum clean all

ENV HOME /home/${USER}
ENV LANG en_US.UTF-8
USER ${USER}

COPY rvmkeys.asc ${HOME}
COPY preload-deps.sh ${HOME}
RUN /bin/bash ${HOME}/preload-deps.sh

WORKDIR /home/${USER}
