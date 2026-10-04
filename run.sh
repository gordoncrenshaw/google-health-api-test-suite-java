#!/usr/bin/env bash
# ==============================================================================
# Google Health API Test Suite - Launcher Script
# Supports 3 Execution Modes:
#   1. ./run.sh menu                  (Command line menu)
#   2. ./run.sh web [port]            (UX using javascript/HTML)
#   3. ./run.sh script <path-to-file> (Through a script file)
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

JAR_FILE="target/health-api-testsuite.jar"

# Build if jar is missing
if [ ! -f "$JAR_FILE" ]; then
    echo ">> Test suite executable not found. Building with Maven..."
    mvn clean package -DskipTests
    echo ">> Build complete!"
fi

MODE="${1:-menu}"

case "$MODE" in
    menu|-m|--menu)
        echo ">> Launching Mode 1: Command Line Menu..."
        shift || true
        java -jar "$JAR_FILE" --menu "$@"
        ;;
    web|-w|--web)
        shift || true
        PORT="${1:-8080}"
        shift || true
        echo ">> Launching Mode 2: Web UX (HTML / JavaScript) on port $PORT..."
        java -jar "$JAR_FILE" --web "$PORT" "$@"
        ;;
    script|-s|--script)
        shift || true
        SCRIPT_PATH="${1:-scripts/sample_suite.yaml}"
        shift || true
        echo ">> Launching Mode 3: Script Runner with $SCRIPT_PATH..."
        java -jar "$JAR_FILE" --script "$SCRIPT_PATH" "$@"
        ;;
    help|-h|--help)
        java -jar "$JAR_FILE" --help
        ;;
    *)
        # If user passed a file path ending in .yaml or .json, run as script
        if [[ "$MODE" == *.yaml || "$MODE" == *.json ]]; then
            java -jar "$JAR_FILE" --script "$MODE"
        else
            echo "Unknown mode: $MODE"
            echo "Usage: ./run.sh [menu | web <port> | script <path-to-script>]"
            exit 1
        fi
        ;;
esac
