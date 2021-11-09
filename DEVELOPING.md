Developer Guide
===============

### Scripted Tests

To ensure scripted tests are ran with the artifacts published with `crossSbtVersions`, the normal scripted `sbt-test` are in a separate sbt project [scripted](scripted/build.sbt).

```bash
  # Publish both sbt 0.13.x and 1.x plugin and libraries
  sbt -v clean ^test ^publishLocal
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

