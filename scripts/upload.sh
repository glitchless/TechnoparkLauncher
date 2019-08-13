#!/usr/bin/env bash

cd ../

rm -rf build/libs/

./gradlew fatJar

JARPATH="build/libs/$(ls build/libs/ | tail -n +1 | head -1)"

SHA256=$(java -jar scripts/Hasher.jar "$JARPATH")
UPLOAD_FILE=${JARPATH#*-}
VERSION=${UPLOAD_FILE%.*}

rm launcher.json
JSON=$(jo version="$VERSION" downloadFullPath="https://minecraft.glitchless.ru/$VERSION.jar" SHA-256="$SHA256")
echo "${JSON}" >> launcher.json
scp "${JARPATH}" root@minecraft.glitchless.ru:/var/www/html/"${VERSION}".jar
scp launcher.json root@minecraft.glitchless.ru:/var/www/html/launcher.json