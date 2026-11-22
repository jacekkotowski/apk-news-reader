#!/bin/bash
# Compiles the Android-free classes (listed in tests/core.txt) with the tests and runs them on the PC.
set -e
cd "$(dirname "$0")/.."
S=app/src/pl/stashreader; O=out_test; rm -rf $O; mkdir -p $O
CORE=$(sed "s#^#$S/#" tests/core.txt)
javac -nowarn --release 11 -encoding UTF-8 -d $O $CORE tests/T.java 2>&1 | grep -v "^Picked up" || true
[ -f $O/pl/stashreader/T.class ] || { echo "Test compile failed"; exit 1; }
java -cp $O pl.stashreader.T 2>&1 | grep -v "^Picked up"; [ "${PIPESTATUS[0]}" = 0 ]
