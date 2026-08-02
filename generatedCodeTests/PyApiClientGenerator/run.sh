mkdir -p generated/
cp ../../src/test/resources/org/codegen/generators/PyApiClientGenerator/endpointsOutput.py generated/api.py

for image in 'python:3.11-alpine' \
             'python:3.12-alpine' \
             'python:3.13-alpine' \
             'python:3.14-alpine'
do
  export IMAGE="$image"
  export CONTAINER_NAME="py_api_client_$(echo "$image" | tr -c '[:alnum:]_-' '-')"
  docker compose --progress quiet up --build --abort-on-container-exit --no-attach mock_server --no-attach mock_server_secured
done
