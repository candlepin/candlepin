#!/bin/bash

# Generates Java translation source files from gettext .po files.
# Used by the Maven build (exec-maven-plugin in maven/war/pom.xml).
#
# Usage: generate_translations.sh <po-dir> <output-dir>
#
# Fail fast so unsuccessful msgfmt invocations are not silently ignored.
set -e

INPUT="${1:-po}"
OUTPUT="${2:-target/generated-sources/msgfmt/gen/java}"

# Create a temporary directory for intermediate build artifacts
BUILD_DIR=$(mktemp -d -q msgfmt.XXXXXX)

# Always delete the build directory on exit
trap 'rm -rf -- "$BUILD_DIR"' EXIT

mkdir -p "$OUTPUT"
echo "Writing i18n catalog source files to: $OUTPUT"

# msgfmt only processes one .po file at a time (locale must be specified explicitly),
# so we loop and merge results into the output directory.
shopt -s nullglob
po_files=("$INPUT"/*.po)
shopt -u nullglob

if [ ${#po_files[@]} -eq 0 ]; then
    echo "No .po files found in: $INPUT" >&2
    exit 1
fi

for po_file in "${po_files[@]}"; do
    echo "Processing file: $po_file"
    rm -rf "$BUILD_DIR"/*

    locale="$(basename "$po_file" .po)"
    (
    set -x
    msgfmt --java2 --source -r org.candlepin.common.i18n.Messages -l "$locale" -d "$BUILD_DIR" "$po_file"
    )

    cp -r "$BUILD_DIR/." "$OUTPUT/"
done
