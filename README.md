![](growthbook-hero-java.png)

[![](https://jitpack.io/v/growthbook/growthbook-sdk-java.svg)](https://jitpack.io/#growthbook/growthbook-sdk-java)

# GrowthBook Java SDK

- [Requirements](#requirements)
- [Documentation](#documentation)
- [Contributing](#contributing)
  - [Releasing a new version](#releasing-a-new-version)

## Requirements

- Java version 1.8 or later
- The library uses Slf4j for logging. You will need to add an appropriate slf4j binding for the logging framework that your project uses.


## Documentation

- [Usage Guide](https://docs.growthbook.io/lib/java)
- [JavaDoc class documentation](https://growthbook.github.io/growthbook-sdk-java/)


## Contributing

### Releasing a new version

For now we are manually managing the version number.

When making a new release, ensure the file `growthbook/sdk/java/Version.java` has the version matching the tag and release. For example, if you are releasing version `0.3.0`, the following criteria should be met:

- the tag should be `0.3.0`
- the release should be `0.3.0` 
- the contents of the `Version.java` file should include the version as `static final String SDK_VERSION = "0.3.0";`