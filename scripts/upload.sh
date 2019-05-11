#!/usr/bin/env bash

cd ../

./gradlew fatJar

JARPATH=build/libs/TechnoparkLauncher-1.0-SNAPSHOT.jar
SHA256=$(java -jar scripts/Hasher.jar $JARPATH)
VERSION="1.2.$(date +%s)"

rm launcher.json
JSON=$(jo version="$VERSION" downloadFullPath="https://minecraft.glitchless.ru/$VERSION.jar" SHA-256="$SHA256")
echo ${JSON} >> launcher.json
scp ${JARPATH} root@minecraft.glitchless.ru:/var/www/html/${VERSION}.jar
scp launcher.json root@minecraft.glitchless.ru:/var/www/html/launcher.json