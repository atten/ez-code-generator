import os
from functools import wraps

from flask import request, abort

ALLOWED_USER_AGENT = os.environ.get('ALLOWED_USER_AGENT')
AUTH_HEADER_NAME = os.environ.get('AUTH_HEADER_NAME')
AUTH_HEADER_VALUE = os.environ.get('AUTH_HEADER_VALUE')


def auth_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if ALLOWED_USER_AGENT and request.headers.get('user-agent') != ALLOWED_USER_AGENT:
            return abort(403)
        if AUTH_HEADER_NAME and request.headers.get(AUTH_HEADER_NAME) != AUTH_HEADER_VALUE:
            return abort(403)
        return f(*args, **kwargs)
    return decorated_function


def item_factory() -> dict:
    return {
        "timestamp": "2024-07-14T12:00:00Z",
        "duration": "PT10H50S",
        "enum_value": "value 1",
        "json_value": {'foo': 1, 'bar': [{'foobar': 2}]},
        "customName": 100.5,
        "listValue": [100, 200, 300],
        "non_existent_field": "text",
    }

def item_container_factory() -> dict:
    return {
        "basic": item_factory(),
        "basics": [item_factory(), item_factory()],
        "basic_nullable_list": None,
    }
