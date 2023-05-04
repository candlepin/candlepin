#!/bin/bash

# This script is used by the maven build (see pom.xml) to generate
# translation source files based on the gettext .po files.

# Fail fast to make sure we don't silently ignore unsuccessful msgfmt invocations (or other errors).
set -e

INPUT=po
OUTPUT=target/generated-sources/msgfmt/gen/java

# Create a temporary directory that we can freely munge with temporary build artifacts
BUILD_DIR=$(mktemp -d -q msgfmt.XXXXXX)

# Always delete the build directory on exit
trap 'rm -rf -- "$BUILD_DIR"' EXIT

# Ensure our output directory exists
mkdir -p $OUTPUT
echo "Writing i18n catalog source files to: $OUTPUT"

# Impl note:
# msgfmt does not allow us to convert more than one .po file at a time due to the need to
# specify the locale directly for the Java command. For this reason we do the initial
# generation in the build directory and then copy the artifact the output directory.
for po_file in $INPUT/*.po
do
  echo "Processing file: $po_file"
  # Ensure the the build directory is empty, as msgfmt complains if any artifacts
  # a previous build already exist when building sources
  rm -rf $BUILD_DIR/*

  locale="$(basename $po_file .po)"
  (
  set -x # echo out the commands as you run them
  msgfmt --java2 --source -r org.candlepin.common.i18n.Messages -l $locale -d $BUILD_DIR $po_file
  )

  # Copy everything over to the generated-sources directory
  cp -r $BUILD_DIR/* $OUTPUT/
done
