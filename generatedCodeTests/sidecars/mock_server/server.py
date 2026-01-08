from flask import Flask, request

from utils import item_factory, auth_required, item_container_factory

app = Flask(__name__)


@app.get("/api/v1/ping")
@auth_required
def ping():
    return 'pong'


@app.get("/api/v1/basic")
@auth_required
def get_basic_dto_list():
    return [item_factory()]


@app.get("/api/v1/basic/<timestamp>")
@auth_required
def get_basic_dto_by_timestamp(timestamp: str):
    result = item_factory()
    result['timestamp'] = timestamp
    return result


@app.get("/api/v1/container")
@auth_required
def get_container_dto():
    return item_container_factory()


@app.post("/api/v1/basic")
@auth_required
def create_basic_dto():
    return item_factory()


@app.post("/api/v1/basic/bulk")
@auth_required
def create_basic_dto_bulk():
    response = []
    for item in request.json:
        response.append(item_factory())
    return response
