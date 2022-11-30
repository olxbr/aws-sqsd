# AWS SQS Worker Daemon

A simple alternative to the Amazon SQS Daemon ("sqsd") used on AWS Beanstalk worker tier instances.


## Configuration
> *IMPORTANT:* In order for `sqsd` to work, you have to have configured the AWS Authentication Keys on you environment either as ENV VARS or using any of the other methods that AWS provides. For ways to do this, go [here](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html).

#### Using Environment Variables

| **Property**                            | **Default**            | **Required**                       | **Description**                                                           |
|-----------------------------------------|------------------------|------------------------------------|---------------------------------------------------------------------------|
| `AWS_DEFAULT_REGION`                    | `us-east-1`            | no                                 | The region name of the AWS SQS queue.                                     |
| `AWS_ACCESS_KEY_ID`                     | -                      | yes                                | The access key to access the AWS SQS queue.                               |
| `AWS_SECRET_ACCESS_KEY`                 | -                      | yes                                | The secret key to access the AWS SQS queue.                               |
| `SQSD_QUEUE_URL`                        | -                      | yes                                | Your queue URL.                                                           |
| `SQSD_WORKER_CONCURRENCY`               | `10`                   | no                                 | Max number of messages process in parallel.                               |
| `SQSD_WAIT_TIME_SECONDS`                | `20` (max: `20`)       | no                                 | Long polling wait time when querying the queue.                           |
| `SQSD_WORKER_HTTP_URL`                  | `http://127.0.0.1:80/` | no                                 | Your service endpoint/path where to POST the messages.                    |
| `SQSD_WORKER_HTTP_REQUEST_CONTENT_TYPE` | `application/json`     | yes                                | Message MIME Type.                                                        |
| `SQSD_WORKER_TIMEOUT`                   | `30000`                | no                                 | Max time that waiting for a worker response in milliseconds.              |
| `SQSD_WORKER_HEALTH_URL`                | `http://127.0.0.1:80/` | no                                 | Your service endpoint/path for your service health.                       |
| `SQSD_WORKER_HEALTH_WAIT_TIME`          | `30`                   | no                                 | Time to between health checks.                                            |


## How to build
```
sbt compile
sbt universal:packageZipTarball
```
or using an SBT's docker image

```
docker run --rm -it -v $PWD:/target -v $HOME/.ivy2:/root/.ivy2 -v $HOME/.m2:/root/.m2 -w /target hseeberger/scala-sbt:8u151-2.12.4-1.1.1 sbt compile
docker run --rm -it -v $PWD:/target -v $HOME/.ivy2:/root/.ivy2 -v $HOME/.m2:/root/.m2 -w /target hseeberger/scala-sbt:8u151-2.12.4-1.1.1 sbt universal:packageZipTarball
``` 

## How to build the docker image
```
docker build --tag aws-sqsd:<some version> .
docker build --tag <some_company>/aws-sqsd:<some version> .
```

## How to use

You should use the pre created GZVR's image
````
docker -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e SQSD_QUEUE_URL=<queue-url> -it -d run ${{ secrets.CONTAINER_REGISTRY_HOST }}/aws-sqsd
````

or the image created by yourself

````
docker -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e SQSD_QUEUE_URL=<queue-url> -it -d run some_image
````

## Contributing

If you found a bug in the source code or if you want to contribute with new features,
you can help submitting an issue, even better if you can submit a pull request :)