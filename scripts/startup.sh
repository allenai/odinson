
if [ -e /local/data/${dataset_name} ]
then
    curl https://storage.googleapis.com/ai2i/SPIKE/${dataset_name}.tar.gz | tar -C /local/data -xzv

/local/bin/odinson-rest-api
