if [ $# -ne 2 ]
  then
    echo "USAGE: startup.sh <dataset_name> <dataset_version>"
    exit 1
fi

DATASET_NAME=$1
DATASET_VERSION=$2
DATASET_URL=https://storage.googleapis.com/ai2i/SPIKE/datasets/${DATASET_NAME}/${DATASET_VERSION}/${DATASET_NAME}-odinson-index.tar.gz

if [ ! -d /local/data/index ]
then
    echo "Downloading dataset from: ${DATASET_URL}..."
    curl  ${DATASET_URL} | tar -C /local/data -xzv
fi

/local/bin/odinson-rest-api
