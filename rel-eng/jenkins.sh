#!/bin/bash

# jenkins has about 31 ways to set this, yet it never
# seems right. Remove this and $PATH when sorted.
export JAVA_HOME=/usr/lib/jvm/java

# All of the rvm stuff is so we can get buildr installed

# add rvm to path
export PATH=/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:${HOME}.local/bin:${HOME}/bin:${HOME}/.rvm/bin

source "${HOME}/.rvm/scripts/rvm"

RVM_RUBY="${RVM_RUBY:-ruby-2.0.0-p353}"
rvm use "${RVM_RUBY}"

# use jenkins job name, or NO-JOB-NAME if not available
JOB_NAME="${JOB_NAME:-NO-JOB-NAME}"
GEMSET_NAME="${JOB_NAME}-gemset"

# assume rvm_gemset_create_on_use_flag=1
# rvm gemset create "${GEMSET_NAME}"
# Otherwise, this will complain for non existing gemset names

RUBY_GEMSET="${RVM_RUBY}@${GEMSET_NAME}"
rvm use "${RUBY_GEMSET}"

# If we want to use "vendor/cache" local
# cache (as opposed to "global" gemset cache)
LOCAL_GEMS="${LOCAL_GEMS:-}"
bundle install ${LOCAL_GEMS}

# export so we can archive it with the test results
rvm gemset export

# so we have a consistent filename to archive
GEMSET_EXPORT="${GEMSET_EXPORT:-exported-gemset.gems}"
mv "${GEMSET_NAME}.gems" "${GEMSET_EXPORT}"

# Set env variables used in buildfile to turn on test requirements
nopo="${NOPO:-}"
findbugs="${FINDBUGS:-}"
pmd="${PMD:-}"
export nopo findbugs pmd

BUILDR_TARGETS="${BUILDR_TARGETS:-help:tasks}"

# exit on any build failures or non zero return codes
# Not set before before it would fail a lot.
set -e

buildr ${BUILDR_TARGETS}

# We don't care if the gemset delete fails
#set +e

# delete it
#rvm --force gemset delete "${GEMSET_NAME}"
