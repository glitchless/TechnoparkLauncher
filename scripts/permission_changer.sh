#!/bin/bash

URL="$1"
FOLDER_NAME="jre8u282-full"
filename=$(basename -- "$URL")
extension="${filename#*.}"
UNZIP_DIR="tmp"
rm -rf $UNZIP_DIR
mkdir -p $UNZIP_DIR

curl $URL --output $filename

if [[ "$extension" == "tar.gz" ]]; then
  tar -C $UNZIP_DIR -zxvf "$filename"
else
  unzip "$filename" -d $UNZIP_DIR
fi

rm "$filename"

chmod -R 777 $UNZIP_DIR

THIS_CONTEXT=$(pwd)
cd $UNZIP_DIR || exit
if [[ "$extension" == "tar.gz" ]]; then
  tar -czvf "$THIS_CONTEXT/new-$filename" "$FOLDER_NAME"
else
  zip -r "$THIS_CONTEXT/new-$filename" "$FOLDER_NAME"
fi
cd "$THIS_CONTEXT" || exit



