#!/bin/bash

URL="$1"
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
tmp_files=(*)
FOLDER_NAME="${tmp_files[0]}"
if [[ "$extension" == "tar.gz" ]]; then
  tar -czvf "$THIS_CONTEXT/$filename" "$FOLDER_NAME"
else
  zip -r "$THIS_CONTEXT/$filename" "$FOLDER_NAME"
fi
cd "$THIS_CONTEXT" || exit
