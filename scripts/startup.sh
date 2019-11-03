if [ $# -ne 2 ]
  then
    echo "USAGE: startup.sh <dataset_name> <dataset_version>"
    exit 1
fi

dataset_name=$1
dataset_version=$2
if [ ! -d /local/data/index ]
then
    curl https://storage.googleapis.com/ai2i/SPIKE/datasets/${dataset_name}/dataset_version/${dataset_name}.tar.gz | tar -C /local/data -xzv
fi

/local/bin/odinson-rest-api
