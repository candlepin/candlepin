#!/bin/sh

if [ "$1" != "" ]; then
    IMAGE_NAME=$1
else
    echo "No image specified" >&2
    exit 1
fi


CP_VERSION="$(docker run -ti --rm candlepin/$IMAGE_NAME:latest rpm -q --queryformat '%{VERSION}' candlepin)"

if (! echo $CP_VERSION | grep -E -q --regex="^[0-9]+\.[0-9]+.*") then
  # We probably checked it out from git
  CP_VERSION="$(docker run -ti --rm candlepin/$IMAGE_NAME:latest cd candlepin && git describe | cut -d- -f 2)"
fi

if (! echo $CP_VERSION | grep -E -q --regex="^[0-9]+\.[0-9]+.*") then
  echo "Unable to determine Candlepin version for image $IMAGE_NAME" >&2
  exit 1
fi

echo "Found candlepin version: $CP_VERSION"
