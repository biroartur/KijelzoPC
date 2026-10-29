#!/bin/bash
# KijelzoPC start script for Raspberry Pi (Linux)
# Run from the directory containing KijelzoPC.jar and server.properties

cd "$(dirname "$0")"
java -Dfile.encoding=utf-8 -jar out/KijelzoPC.jar
