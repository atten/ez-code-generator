mkdir -p generated/
cp ../../src/test/resources/org/codegen/generators/PyDataclassGenerator/entitiesOutput.py generated/dto.py

for image in 'python:3.9-alpine' \
             'python:3.10-alpine' \
             'python:3.11-alpine' \
             'python:3.12-alpine'
do
  export IMAGE="$image"
  export CONTAINER_NAME="py_dataclass_$(echo "$image" | tr -c '[:alnum:]_-' '-')"
  docker compose --progress quiet up --build --abort-on-container-exit
done
