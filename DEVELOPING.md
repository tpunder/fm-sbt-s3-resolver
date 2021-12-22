Developer guide
===============

### Project Structure

* `plugin` this is the home of `S3ResolverPlugin`
* `coursier-handler` this is the core library that also includes `coursier.cache.protocol.S3Handler`, which is suitable for coursier support.
* `scripted` this is a separate project that includes integration tests using a local S3 server (via localstack docker container) and additional [scripted](https://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html) tests.

### Scripted Tests

Because we're both a library and an sbt plugin, we use crossScalaVersions rather than crossSbtVersions for cross building. So you can use commands like `+test`, `+publishLocal`, `+publishSigned`, and etc.

To allow different `sbtCrossVersions` to be invoked, there is a separate sbt [scripted project](scripted/build.sbt). Each time `scripted` is ran, the local s3 instance is reset to bootstrap the bucket `maven.custom` with the contents of [scripted/test/s3/maven.custom](scripted/test/s3/maven.custom)

```bash
  # Required for running the s3 localstack container
  export DOCKER_HOST=unix:///var/run/docker.sock
  # Publish both sbt 0.13.x and 1.x plugin and libraries
  sbt -v clean +test +publishLocal
  pushd scripted
  # Run scripted tests using sbt 0.13.18
  sbt -v ++2.10.7 ^^0.13.18 scripted
  # Run scripted tests with all supported sbt 1.x versions
  sbt -v ++2.12.15 \
    ^^1.1.6 scripted \
    ^^1.2.8 scripted \
    ^^1.3.13 scripted \
    ^^1.4.9 scripted \
    ^^1.5.8 scripted \
    ^^1.6.1 scripted
  popd
```

### Frequently Asked Questions

- [Could not find a valid Docker environment](#could-not-find-a-valid-docker-environment)


#### Could not find a valid Docker environment

```
[info] [S3ScriptedPlugin] Starting Embedded S3 Server...
10:54:22.620 [main] ERROR org.testcontainers.dockerclient.DockerClientProviderStrategy - Could not find a valid Docker environment. Please check configuration. Attempted configurations were:
10:54:22.620 [main] ERROR org.testcontainers.dockerclient.DockerClientProviderStrategy - As no valid configuration was found, execution cannot continue
[error] java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
[error] 	at org.testcontainers.dockerclient.DockerClientProviderStrategy.lambda$getFirstValidStrategy$4(DockerClientProviderStrategy.java:156)
[error] 	at java.util.Optional.orElseThrow(Optional.java:290)
[error] 	at org.testcontainers.dockerclient.DockerClientProviderStrategy.getFirstValidStrategy(DockerClientProviderStrategy.java:148)
[error] 	at org.testcontainers.DockerClientFactory.getOrInitializeStrategy(DockerClientFactory.java:146)
```

The scripted tests require docker to be running locally, and set a proper `DOCKER_HOST` environment variable:

    export DOCKER_HOST=unix:///var/run/docker.sock
