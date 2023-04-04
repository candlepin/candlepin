#!/bin/bash

move_artifact() {
    if [ -f "$1" ] && [ -d "$2" ]; then
        cp -v "$1" "$2" || true
    fi
}

collect_artifacts() {
    # If the caller mounted a volume at /artifacts, copy server logs out:
    ARTIFACT_DIR="/candlepin-dev/artifacts/"

    # We only want these from /var/log/candlepin
    CANDLEPIN_LOGS=(
        access.log
        audit.log
        candlepin.log
        error.log
    )
    # prepend full file path for maximum lazy typers
    for ((i=0; i<${#CANDLEPIN_LOGS[*]}; i++)); do
        CANDLEPIN_LOGS[${i}]="/var/log/candlepin/${CANDLEPIN_LOGS[${i}]}"
    done

    TOMCAT_LOGS=$(find /var/log/tomcat/ -maxdepth 1 -type f)
    ARCHIVE_LOGS=("${CANDLEPIN_LOGS[@]}" "${TOMCAT_LOGS[@]}")

    if [ -d "${ARTIFACT_DIR}" ]; then
        echo "Collecting artifacts..."

        # It's entirely possible for these to not exist, so we'll copy them if we can, but if we
        # fail, we shouldn't abort
        for i in ${ARCHIVE_LOGS[@]}; do
            move_artifact "${i}" "${ARTIFACT_DIR}"
        done
    fi
}

collect_artifacts
