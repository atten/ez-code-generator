import logging
import os
import types
from datetime import datetime, timezone, timedelta
from urllib.parse import urlparse

import pytest
from marshmallow.exceptions import ValidationError

from generated.api import Generated, AllDataclassesCollection as dto, AllConstantsCollection as constants

BASE_URL = os.environ['BASE_URL']
SECURED_BASE_URL = os.environ['SECURED_BASE_URL']


@pytest.fixture()
def basic_dto() -> dto.BasicDto:
    return dto.BasicDto(
        timestamp=datetime.now(),
        duration=timedelta(minutes=5),
        enum_value=constants.EnumValue.VALUE_1,
        json_value={'foo': 5},
        documented_value=2.5,
        list_value=[50, 100, 150]
    )

def test_get():
    api = Generated(base_url=BASE_URL)
    result = api.get_container_dto()

    assert isinstance(result, dto.ContainerDto)
    assert isinstance(result.basic_single, dto.BasicDto)
    assert isinstance(result.basic_list[0], dto.BasicDto)


def test_get_parametrized():
    api = Generated(base_url=BASE_URL)
    timestamp = datetime.now(tz=timezone.utc)
    result = api.get_basic_dto_by_timestamp(timestamp)

    assert isinstance(result, dto.BasicDto)


def test_get_list():
    api = Generated(base_url=BASE_URL)
    result = api.get_basic_dto_list()

    assert isinstance(result, types.GeneratorType)
    assert isinstance(next(result), dto.BasicDto)


def test_post(basic_dto):
    api = Generated(base_url=BASE_URL)
    result = api.create_basic_dto(basic_dto)

    assert isinstance(result, dto.BasicDto)


def test_post_list_required_fields_only(basic_dto):
    api = Generated(base_url=BASE_URL)
    result = api.create_basic_dto_bulk([basic_dto])

    assert isinstance(result, types.GeneratorType)
    assert isinstance(next(result), dto.BasicDto)


def test_post_empty_list():
    api = Generated(base_url=BASE_URL)
    result = api.create_basic_dto_bulk([])

    assert isinstance(result, types.GeneratorType)
    with pytest.raises(StopIteration):
        next(result)


def test_post_request_wrong_enum_value(basic_dto):
    api = Generated()
    basic_dto.enum_value = constants.EnumValue.VALUE_1 + 'azaza'
    payload = [basic_dto]

    with pytest.raises(ValidationError):
        result = api.create_basic_dto_bulk(payload)
        next(result)


def test_403():
    api = Generated(base_url=SECURED_BASE_URL, max_retries=0)
    with pytest.raises(RuntimeError):
        api.ping()


def test_retries(caplog):
    api = Generated(base_url=SECURED_BASE_URL, max_retries=3, retry_timeout=0.1, logger=logging.getLogger())
    with pytest.raises(RuntimeError):
        with caplog.at_level(logging.WARNING):
            api.ping()

    assert 'got HTTPError on BaseJsonHttpClient._mk_request, attempt 1 / 3' in caplog.messages
    assert 'got HTTPError on BaseJsonHttpClient._mk_request, attempt 2 / 3' in caplog.messages
    assert 'got HTTPError on BaseJsonHttpClient._mk_request, attempt 3 / 3' in caplog.messages


def test_user_agent_and_headers():
    api = Generated(
        base_url=SECURED_BASE_URL,
        user_agent=os.environ['SECURED_USER_AGENT'],
        headers={
            os.environ['SECURED_HEADER_NAME']: os.environ['SECURED_HEADER_VALUE']
        }
    )
    api.ping()


def test_keepalive_connection(basic_dto, caplog):
    with caplog.at_level(logging.DEBUG):
        api = Generated(base_url=BASE_URL)
        api.create_basic_dto(basic_dto)
        api.create_basic_dto(basic_dto)
        api.create_basic_dto(basic_dto)
        list(api.get_basic_dto_list())

    location = urlparse(BASE_URL).netloc
    location = location + ':80' if ':' not in location else location

    message = f'Starting new HTTP connection (1): {location}'
    assert message in caplog.messages
    assert caplog.messages.count(message) == 1


def test_use_debug_curl():
    api = Generated(base_url="http://none", use_debug_curl=True, max_retries=0)
    with pytest.raises(RuntimeError) as e:
        api.ping()

    assert 'curl "http://none/api/v1/ping"' in str(e)


def test_custom_exception_class():
    class ApiError(Exception):
        pass

    api = Generated(base_url="http://none", max_retries=0, exception_class=ApiError)
    with pytest.raises(ApiError):
        api.ping()


def test_callable_header():
    def get_header() -> str:
        return os.environ['SECURED_HEADER_VALUE']

    api = Generated(
        base_url=SECURED_BASE_URL,
        user_agent=os.environ['SECURED_USER_AGENT'],
        headers={
            os.environ['SECURED_HEADER_NAME']: get_header
        }
    )
    api.ping()
