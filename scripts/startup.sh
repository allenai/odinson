INDEX_URL="${INDEX_URL:-https://storage.googleapis.com/ai2i/SPIKE/datasets/tacred-train-labeled/tacred-train-odinson-index-ordered-24092019.tar.gz}"
LOCAL_INDEX_PATH=/local/data/index

if [ ! -d ${LOCAL_INDEX_PATH} ]
then
    echo "INFO: Downloading index from: ${INDEX_URL}..."  
    curl  ${INDEX_URL} | tar -C /local/data -xzv || exit 1
else
    echo "INFO: loading existing index from ${LOCAL_INDEX_PATH}..."
fi

/local/bin/odinson-rest-api
