if [ $# -ne 2 ]
  then
    echo "USAGE: startup.sh <index_url>"
    exit 1
fi

INDEX_URL="${INDEX_URL:-https://storage.googleapis.com/ai2i/SPIKE/datasets/tacred-train-labeled/tacred-train-odinson-index-ordered-24092019.tar.gz}"

if [ ! -d /local/data/index ]
then
    echo "Downloading dataset from: ${INDEX_URL}..."  
    curl  ${INDEX_URL} | tar -C /local/data -xzv || exit 1   
fi

/local/bin/odinson-rest-api
