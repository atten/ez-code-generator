# Easy Code Generator

[![pipeline](https://gitlab.com/atten0/ez-code-generator/badges/master/pipeline.svg)](https://gitlab.com/atten0/ez-code-generator/-/pipelines)
[![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg)](http://www.jacoco.org/jacoco)
[![PyPI - Python Version](https://img.shields.io/pypi/pyversions/ez-code-generator?link=https%3A%2F%2Fpypi.org%2Fproject%2Fez-code-generator)](https://pypi.org/project/ez-code-generator)

Console tool for generating API clients, data classes and validators. Written in Kotlin, produces Kotlin and Python code. For the input it takes OpenApi document (JSON/YAML, v2.0 ‒ v3.1.0) or custom codegen schema (JSON).

The main goal of this project is to reduce integration costs between teams in multi-service environments.

### Example

<table>
<tr>
<td> OpenAPI source YAML </td>
<td>

```yaml
definitions:
  BasicDto:
    type: object
    properties:
      a:
        title: Some integer
        description: Field description
        type: integer
        maximum: 255
        minimum: 0
      b:
        title: Timestamp
        type: string
        format: date-time
      c:
        title: Some enum
        type: string
        enum:
          - variant1
          - variant2
          - variant3
        default: variant1
    required:
      - a
```

</td>
</tr>
<tr>
<td> Python generated dataclass </td>
<td>

```python
@dataclass
class BasicDto:
    # Field description
    a: int = field()
    b: datetime | None = None
    c: str | None = "variant1"    
```

</td>
</tr>
<tr>
<td> Kotlin generated classes </td>
<td>

```kotlin
@Serializable
enum class SomeEnum(val value: String) {
    @SerialName("variant1")
    VARIANT_1("variant1"),
    @SerialName("variant2")
    VARIANT_2("variant2"),
    @SerialName("variant3")
    VARIANT_3("variant3"),
}

@Serializable
data class BasicDto(
    // Field description
    val a: Int,
    @Contextual
    val b: Instant? = null,
    val c: SomeEnum? = SomeEnum.VARIANT_1,
)
```

</td>
</tr>
</table>

> More examples: section [Available generators](#available-generators) below

### Features

- Several python frameworks support (asyncio, django, pure dataclass etc);
- Schema validation, logging, error handling, retries and sessions within generated client out-of-the-box;
- Streamed dump/load (ijson);
- Kotlin experimental support.

### Key differences

*ez-code-generator* is similar to [openapi-generator](https://github.com/openapitools/openapi-generator) except a major aspect: **a single output file instead of a package**. 
It is meant to be straightforward and concise. No extra configuration options, no tricky preparations before use.
Just install dependencies, import generated client class and instantiate it. Then call API methods which take and return generated DTOs:

```python
from client_generated import AllConstantsCollection as consts
from client_generated import AllDataclassesCollection as dto
from client_generated import RestApi

client = RestApi('https://example.com/v1/')
item = client.get_item(a=10)

# item = BasicDto(a=10, b=None, c="variant1")
```

### Common use cases

- Write integration code in agile, fast-moving projects;
- Use code generation in CI;
- Track API changes and check them for backward compatibility on both sides (client/server);
- Build ETL pipelines with transparent interfaces;
- Unify type declarations across languages.

## Install

### Docker image

```
docker image pull registry.gitlab.com/atten0/ez-code-generator:master
```

[Other image versions](https://gitlab.com/atten0/ez-code-generator/container_registry/1706585)

### JAR

Requires: Java 11

Download and extract archive into current dir:

```shell
wget -qO- https://github.com/atten/ez-code-generator/releases/download/v4.1.1/ez-codegen-4.1.1.zip | busybox unzip -
cd ez-codegen-4.1.1/bin/
chmod +x ez-codegen
```

Other versions: [Gitlab](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/build/distributions/?job=publish-archive) | [Github](https://github.com/atten/ez-code-generator/releases)

### Python package

Requires: Java 11, Python3

```shell
pip install ez-code-generator==4.1.1
```

Other versions: [PYPI](https://pypi.org/project/ez-code-generator/#history)

### Build from source

```shell
make build
```

## Usage

    ez-codegen [options] <files/directories/urls...>

Using python package:

    python -m ez-codegen [options] <files/directories/urls...>

Options:

    * -t, --target
      Target implementation
      Possible Values: [KT_DATACLASS, KT_SERIALIZABLE_DATACLASS, KT_INTERFACE, PY_DJANGO_MODEL, PY_API_CLIENT, PY_API_ASYNC_CLIENT, PY_AMQP_BLOCKING_CLIENT, PY_AMQP_GEVENT_CLIENT, PY_MARSHMALLOW_DATACLASS, PY_DATACLASS]

      -n, --name
      Generated class name (inferred from input files if not specified)
      Default: <empty string>

      --include-url-path
      Include only paths containing given strings
      Default: []

      --exclude-url-path
      Do not include paths containing given strings
      Default: []

      -p, --prefixed
      If enabled, add prefix to all fields
      Default: false

      -k, --insecure
      Disable HTTPS certificates validation
      Default: false

      --help
      Show help usage and exit

      --version, -v
      Display version and exit

## Basic guide

### Script for python API client generation from OpenApi

Runs generator in docker container, reads OpenApi spec from URL and saves output to file.

```shell
docker run --rm registry.gitlab.com/atten0/ez-code-generator:master \
    https://gitlab.com/atten0/ez-code-generator/-/raw/master/src/test/resources/input/openApi.json \
    -t PY_API_CLIENT \
    > client_generated.py
```

> Result: [client_generated.py](src/test/resources/org/codegen/generators/PyApiClientGenerator/endpointsOutput.py).

Usage examples: [test_clent.py](generatedCodeTests/PyApiClientGenerator/test_clent.py)

## Available generators

| Target                    | Language/Framework            | Serialization         | Dependencies                                                                            | Example output                                                                                                                                | Coverage                                                                                                                                                                                                                                                                                                                      |
|---------------------------|-------------------------------|-----------------------|-----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KT_DATACLASS              | Kotlin                        | kotlinx.serialization |                                                                                         | [entitiesOutput.kt](src/test/resources/org/codegen/generators/KtSerializableDataclassGenerator/entitiesOutput.kt)                             | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-KtSerializableDataclassGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/KtSerializableDataclassGenerator/htmlcov?job=run-tests-KtSerializableDataclassGenerator) |
| KT_SERIALIZABLE_DATACLASS | Kotlin                        | Jackson               |                                                                                         | [entitiesOutputJacksonEnabled.kt](src/test/resources/org/codegen/generators/KtSerializableDataclassGenerator/entitiesOutputJacksonEnabled.kt) | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-KtSerializableDataclassGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/KtSerializableDataclassGenerator/htmlcov?job=run-tests-KtSerializableDataclassGenerator) |
| PY_API_CLIENT             | Python (3.10 - 3.13)          | Marshmallow           | [requirements.txt](generatedCodeTests/PyApiClientGenerator/requirements.txt)            | [endpointsOutput.py](src/test/resources/org/codegen/generators/PyApiClientGenerator/endpointsOutput.py)                                       | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyApiClientGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyApiClientGenerator/htmlcov?job=run-tests-PyApiClientGenerator)                                     |
| PY_API_ASYNC_CLIENT       | Python asyncio (3.10 - 3.13)  | Marshmallow           | [requirements.txt](generatedCodeTests/PyApiAsyncClientGenerator/requirements.txt)       | [endpointsOutput.py](src/test/resources/org/codegen/generators/PyApiAsyncClientGenerator/endpointsOutput.py)                                  | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyApiAsyncClientGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyApiAsyncClientGenerator/htmlcov?job=run-tests-PyApiAsyncClientGenerator)                      |
| PY_DATACLASS              | Python (3.10 - 3.13)          | Dataclass             | -                                                                                       | [entitiesOutput.py](src/test/resources/org/codegen/generators/PyDataclassGenerator/entitiesOutput.py)                                         | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyDataclassGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyDataclassGenerator/htmlcov?job=run-tests-PyDataclassGenerator)                                     |
| PY_MARSHMALLOW_DATACLASS  | Python (3.10 - 3.13)          | Marshmallow           | [requirements.txt](generatedCodeTests/PyMarshmallowDataclassGenerator/requirements.txt) | [entitiesOutput.py](src/test/resources/org/codegen/generators/PyMarshmallowDataclassGenerator/entitiesOutput.py)                              | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyMarshmallowDataclassGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyMarshmallowDataclassGenerator/htmlcov?job=run-tests-PyMarshmallowDataclassGenerator)    |
| PY_DJANGO_MODEL           | Python (3.10 - 3.13) + Django | -                     | [requirements.txt](generatedCodeTests/PyDjangoModelGenerator/requirements.txt)          | [entitiesOutput.py](src/test/resources/org/codegen/generators/PyDjangoModelGenerator/entitiesOutput.py)                                       | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyDjangoModelGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyDjangoModelGenerator/htmlcov?job=run-tests-PyDjangoModelGenerator)                        |
| PY_AMQP_BLOCKING_CLIENT   | Python3                       | Marshmallow           |                                                                                         | [endpointsOutput.py](src/test/resources/org/codegen/generators/PyAmqpBlockingClientGenerator/endpointsOutput.py)                              | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyAmqpBlockingClientGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyAmqpBlockingClientGenerator/htmlcov?job=run-tests-PyAmqpBlockingClientGenerator)          |
| PY_AMQP_GEVENT_CLIENT     | Python3 + Gevent              | Marshmallow           |                                                                                         | [endpointsOutput.py](src/test/resources/org/codegen/generators/PyAmqpGeventClientGenerator/endpointsOutput.py)                                | [![coverage](https://gitlab.com/atten0/ez-code-generator/badges/master/coverage.svg?job=run-tests-PyAmqpGeventClientGenerator)](https://gitlab.com/atten0/ez-code-generator/-/jobs/artifacts/master/browse/generatedCodeTests/PyAmqpGeventClientGenerator/htmlcov?job=run-tests-PyAmqpGeventClientGenerator)                |

## Support and feedback

For usage questions, feature proposals and bug reports: [github issues page](https://github.com/atten/ez-code-generator/issues).

For other matters: [author's profile with contacts](https://github.com/atten).
