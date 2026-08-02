FROM python:3.13-alpine

WORKDIR /app

ADD requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

ADD . .

CMD flask --app server run --host=0.0.0.0 --port=80
