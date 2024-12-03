![](growthbook-hero-java.png)

[![](https://jitpack.io/v/growthbook/growthbook-sdk-java.svg)](https://jitpack.io/#growthbook/growthbook-sdk-java)

# GrowthBook Java SDK

- [Requirements](#requirements)
- [Documentation](#documentation)
- [Contributing](#contributing)
  - [Releasing a new version](#releasing-a-new-version)

## Requirements

- Java version 1.8 or later


## Documentation

- [Usage Guide](https://docs.growthbook.io/lib/java)
- [JavaDoc class documentation](https://growthbook.github.io/growthbook-sdk-java/)

### GrowthBookClient
`GrowthBookClient` lets you share the same instance for all requests with an ability to accept the user attributes 
while calling the feature methods like `isOn()`. This `GrowthBookClient` instance is decoupled from the `GBContext`, 
creates a singleton featureRepository based on your refreshStrategy and uses the latest features at the time of
evaluation, all managed internally.

```java

// build options to configure your Growthbook instance
Options options = Options.builder()
        .apiHost("https://cdn.growthbook.io")
        .clientKey("sdk-abc123")
        .build();

// Create growthbook instance using the options you need
GrowthBookClient gb = new GrowthBookClient(options);

// call the init method to load features 
gb.initialize();

gb.isOn("featureKey", UserContext.builder()
    .attributesJson("{\"id\" : \"123\"}").build()
);
```


## Contributing

### Releasing a new version

For now we are manually managing the version number.

When making a new release, ensure the file `growthbook/sdk/java/Version.java` has the version matching the tag and release. For example, if you are releasing version `0.3.0`, the following criteria should be met:

- the tag should be `0.3.0`
- the release should be `0.3.0` 
- the contents of the `Version.java` file should include the version as `static final String SDK_VERSION = "0.3.0";`