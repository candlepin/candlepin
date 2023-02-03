#!/bin/bash

usage() {
    SCRIPT_NAME=$(basename $BASH_SOURCE)

    echo "Parses upstream refresh data from a Candlepin log file"
    echo "Usage: $SCRIPT_NAME <input_file>"

    echo ""

    cat <<HELP
OPTIONS:
  -?        Display script usage information
  -h

HELP
}

ARGV=("$@")
while getopts "h?" opt; do
    case $opt in
        h|\?) usage
              exit 1
              ;;

        :   ) echo "Option -$OPTARG requires an argument" >&2
              usage
              exit 1
              ;;

        *   ) break
              ;;
    esac
done

INPUT_FILE=$1


if [ -f "$INPUT_FILE" ]; then
    # Check if this is a gz compressed file that we can should unpack first
    if command -v gunzip > /dev/null && gunzip -t $INPUT_FILE > /dev/null 2>&1; then
        UNPACK="gunzip --stdout $INPUT_FILE"
    else
        UNPACK="cat $INPUT_FILE"
    fi

    if $UNPACK | grep -Eq '^.*\] TRACE org\.candlepin\.controller\.CandlepinPoolManager - \[\{'; then
        $UNPACK | sed -nE 's/^.*\] TRACE org\.candlepin\.controller\.CandlepinPoolManager - (\[\{.*)$/\1/p'
    else
        echo "File does not appear to contain upstream refresh data: $INPUT_FILE"
    fi
else
    echo "Cannot read input file: $INPUT_FILE"
fi
