mkdir -p generated/
cp ../../src/test/resources/org/codegen/generators/PyMarshmallowDataclassGenerator/entitiesOutput.py generated/dto.py

for image in 'python:3.11-alpine' \
             'python:3.12-alpine' \
             'python:3.13-alpine' \
             'python:3.14-alpine'
do
  export IMAGE="$image"
  export CONTAINER_NAME="py_marshmallow_dataclass_$(echo "$image" | tr -c '[:alnum:]_-' '-')"
  docker compose --progress quiet up --build --abort-on-container-exit
done
