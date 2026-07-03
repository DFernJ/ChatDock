FROM python:3.12-alpine
RUN apk add --no-cache git && adduser -D -u 1000 importer && mkdir -p /work && chown importer:importer /work
USER importer
WORKDIR /work
COPY --chown=importer:importer import.py /import.py
