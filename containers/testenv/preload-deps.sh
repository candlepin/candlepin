#!/bin/bash
# The goal of this script is to preload most of the ruby and java deps
# in order to decrease test time.  Designed to be run as the testing user.

set -ve

RUBY_VERSION=${RUBY_VERSION:-"2.5.3"}

# cleanup_env() {
#   sudo /usr/bin/yum clean all
#   sudo /usr/bin/find /var/log/ -type f -exec /usr/bin/cp /dev/null {} \;
# }

git clone https://github.com/candlepin/candlepin.git ${HOME}/candlepin
cd ${HOME}/candlepin

# Setup and install rvm, ruby and pals
gpg2 --import ${HOME}/rvmkeys.asc
# turning off verbose mode, rvm is nuts with this
set +v
curl -sSL https://get.rvm.io | bash -s -- stable --ignore-dotfiles --autolibs=0 --ruby

source ${HOME}/.rvm/scripts/rvm || true
rvm install ${RUBY_VERSION}
rvm use --default ${RUBY_VERSION}
# the above isn't fully working so i guess we do it manually...
echo '[[ -s "$HOME/.rvm/scripts/rvm" ]] && source "$HOME/.rvm/scripts/rvm"' > ~/.bash_profile
set -v

# Install all ruby deps
gem install bundler -v 1.16.1
bundle install --without=proton

# Installs all Java deps into the image, big time saver
./gradlew --no-daemon dependencies

cd /
rm -rf ${HOME}/candlepin