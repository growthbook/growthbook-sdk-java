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

## Installation

### Gradle

To install in a Gradle project, add Jitpack to your repositories, and then add the dependency with the latest version to your project's dependencies.

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.growthbook:growthbook-sdk-java:0.5.0'
}
```

### Maven

To install in a Maven project, add Jitpack to your repositories:

```java
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Next, add the dependency with the latest version to your project's dependencies:

```java
<dependency>
    <groupId>com.github.growthbook</groupId>
    <artifactId>growthbook-sdk-java</artifactId>
    <version>0.5.0</version>
</dependency>
```

> We are proposing two way of initializing SDK:

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

### Manually create separate instance of GBContext, Repository and Growthbook classes

```java
GBFeaturesRepository featuresRepository = GBFeaturesRepository
    .builder()
    .apiHost("https://cdn.growthbook.io")
    .clientKey("<environment_key>") // replace with your client key
    .encryptionKey("<client-key-for-decrypting>") // optional, nullable
    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS) // optional; options: STALE_WHILE_REVALIDATE, SERVER_SENT_EVENTS (default: STALE_WHILE_REVALIDATE)
    .build();

// Optional callback for getting updates when features are refreshed
featuresRepository.onFeaturesRefresh(new FeatureRefreshCallback() {
    @Override
    public void onRefresh(String featuresJson) {
        System.out.println("Features have been refreshed");
        System.out.println(featuresJson);
    }
    @Override
    public void onError(Throwable throwable) {
        System.out.println("Features refreshed with error");
    }
});

try {
    featuresRepository.initialize();
} catch (FeatureFetchException e) {
    // TODO: handle the exception
    e.printStackTrace();
}

// Initialize the GrowthBook SDK with the GBContext and features
GBContext context = GBContext
    .builder()
    .featuresJson(featuresRepository.getFeaturesJson())
    .attributesJson(userAttributesJson)
    .build();

GrowthBook growthBook = new GrowthBook(context);

growthBook.isOn("featureKey");
```

## Usage

- The `evalFeature()` method evaluates a feature based on the provided parameters.
It takes three arguments: a string representing the unique identifier of the feature,
a generic class valueTypeClass that specifies the type of the result value (e.g., Integer, String, Boolean),
an UserContext object, which contains attributes, forceVariations and forceFeatureValues to provide a more flexible way of evaluating features.
The method returns a FeatureResult object, which contains the evaluated result of the feature along with any additional metadata.

```java
public <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass, UserContext userContext);
```

- `getFeatureValue()` the same purpose as in `evalFeature()`, but have ability to provide default value

```java
public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass, UserContext userContext);
```

- The `isOn()` / `isOff()` method takes a string argument, which is the unique identifier for the feature, and UserContext, which contains attributes, forceVariations and forceFeatureValues to provide a more flexible way of evaluating features. Functions return the feature state on/off

```java
public Boolean isOn(String featureKey, UserContext userContext);

public Boolean isOff(String featureKey, UserContext userContext);
```

- The `run()` method takes an Experiment object and UserContext. Function returns an ExperimentResult

```java
public <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment, UserContext userContext);
```

- If you changed, added or removed any features, you can call the `refreshCache()` / `refreshCacheForRemoteEval()` method to clear the cache and download the latest feature definitions.

```java
public void refreshFeature();

public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval);
```

## Remote Evaluation

This mode brings the security benefits of a backend SDK to the front end by evaluating feature flags exclusively on a
private server. Using Remote Evaluation ensures that any sensitive information within targeting rules or unused feature
variations are never seen by the client. Note that Remote Evaluation should not be used in a backend context.

You must enable Remote Evaluation in your SDK Connection settings. Cloud customers are also required to self-host a
GrowthBook Proxy Server or custom remote evaluation backend.

To use Remote Evaluation, set the `FeatureRefreshStrategy = REMOTE_EVAL_STRATEGY` property to your Repository or Options instance. A new evaluation API call will be
made any time a user attribute or other dependency changes.

> If you would like to implement Sticky Bucketing while using Remote Evaluation, you must configure your remote evaluation
> backend to support Sticky Bucketing. You will not need to provide a StickyBucketService instance to the client side SDK.

## Sticky Bucketing

By default, GrowthBook does not persist assigned experiment variations for a user.
We rely on deterministic hashing to ensure that the same user attributes always map to the same experiment variation.
However, there are cases where this isn't good enough. For example, if you change targeting conditions
in the middle of an experiment, users may stop being shown a variation even if they were previously bucketed into it.
Sticky Bucketing is a solution to these issues. You can provide a Sticky Bucket Service to the GrowthBook instance
to persist previously seen variations and ensure that the user experience remains consistent for your users.

Sticky bucketing ensures that users see the same experiment variant, even when user session, user login status, or
experiment parameters change. See the [Sticky Bucketing docs](https://docs.growthbook.io/app/sticky-bucketing) for more
information. If your organization and experiment supports sticky bucketing, you can implement an instance of
the `StickyBucketService` to use Sticky Bucketing. For simple bucket persistence using the CachingLayer.

Sticky Bucket documents contain three fields:

- attributeName - The name of the attribute used to identify the user (e.g. id, cookie_id, etc.)
- attributeValue - The value of the attribute (e.g. 123)
- assignments - A dictionary of persisted experiment assignments. For example: {"exp1__0":"control"}

The attributeName/attributeValue combo is the primary key.

Here's an example implementation using a theoretical db object:

```java
public class InMemoryStickyBucketServiceImpl implements StickyBucketService {
    private final Map<String, StickyAssignmentsDocument> localStorage;

    /**
     * Constructs a new {@code InMemoryStickyBucketServiceImpl} with the specified local storage.
     *
     * @param localStorage a map to store sticky assignments documents in memory.
     */
    public InMemoryStickyBucketServiceImpl(Map<String, StickyAssignmentsDocument> localStorage) {
        this.localStorage = localStorage;
    }

    /**
     * Method for getting all assignments document from cache (in memory: hashmap)
     *
     * @param attributeName  attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @param attributeValue attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @return StickyAssignmentsDocument
     */
    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        return localStorage.get(attributeName + "||" + attributeValue);
    }

    /**
     * Method for saving assignments document to cache (in memory: hashmap)
     *
     * @param doc StickyAssignmentsDocument
     */
    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        localStorage.put(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc);
    }

    /**
     * Method for getting sticky bucket assignments from cache (in memory: hashmap) by attributes of context
     *
     * @param attributes Map of String key and String value that you have in GBContext
     * @return Map with key String and value StickyAssignmentsDocument
     */
    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            StickyAssignmentsDocument doc = getAssignments(key, value);

            if (doc != null) {
                String docKey = doc.getAttributeName() + "||" + doc.getAttributeValue();
                docs.put(docKey, doc);
            }
        }

        return docs;
    }
}
```

## Contributing

### Releasing a new version

We use [Release-Please](https://github.com/googleapis/release-please) to automate our release process. The release process follows these steps:

1. **Make changes using Conventional Commits**: When making changes, format your commit messages following the [Conventional Commits](https://www.conventionalcommits.org/) spec:
   - `fix: message` - for bug fixes (triggers a patch version bump)
   - `feat: message` - for new features (triggers a minor version bump)
   - `feat!: message` or `fix!: message` - for breaking changes (triggers a major version bump)
   - `docs: message` - for documentation changes (no version bump)
   - `chore: message` - for maintenance changes (no version bump)

2. **Automated Release PR**: When commits are pushed to the `main` branch, Release-Please will automatically create or update a release PR that:
   - Updates the version in `gradle.properties` and `Version.java`
   - Updates the `CHANGELOG.md` with all the changes since the last release
   - Groups changes by type (features, fixes, etc.)

3. **Review and Merge**: Review the Release PR and merge it when ready to trigger a release.

4. **Automated Release**: Upon merging the Release PR, the action will:
   - Create a Git tag for the new version
   - Create a GitHub Release with release notes
   - Upload build artifacts to the GitHub Release

5. **Jitpack Integration**: Jitpack will automatically detect the new release tag and make it available for download.

No manual version updates are required!
