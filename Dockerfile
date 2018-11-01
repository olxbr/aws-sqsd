FROM java:8-jdk-alpine
MAINTAINER Alexandre Silva (alexandre.silva@gzvr.com.br)

COPY ./target/universal/aws-sqsd-0.1.tgz /usr/local/app/aws-sqsd-0.1.tgz

WORKDIR /usr/local/app

RUN tar xf aws-sqsd-0.1.tgz
RUN rm aws-sqsd-0.1.tgz
RUN apk add --no-cache bash

WORKDIR /usr/local/app/aws-sqsd-0.1

ENTRYPOINT ["./bin/aws-sqsd"]
