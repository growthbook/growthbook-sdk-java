# Changelog

## [0.10.6](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.5...0.10.6) (2026-01-29)


### Features

* add support for regexi operator ([#191](https://github.com/growthbook/growthbook-sdk-java/issues/191)) ([e65a514](https://github.com/growthbook/growthbook-sdk-java/commit/e65a514b858b5ea46ecf59f3e9025a49a11c75cb))
* Implement ETag caching ([#187](https://github.com/growthbook/growthbook-sdk-java/issues/187)) ([16ef464](https://github.com/growthbook/growthbook-sdk-java/commit/16ef4641e835c0f0279dc8c7d906bd63ed6a091f))

## [0.10.5](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.4...0.10.5) (2025-12-18)


### Features

* enable refresh time customization ([#186](https://github.com/growthbook/growthbook-sdk-java/issues/186)) ([63c97b0](https://github.com/growthbook/growthbook-sdk-java/commit/63c97b08f1f1b887472b7233d0729ff0be4cdcf6))


### Bug Fixes

* add proper client shutdown method ([#188](https://github.com/growthbook/growthbook-sdk-java/issues/188)) ([3486a61](https://github.com/growthbook/growthbook-sdk-java/commit/3486a6123ca03b377252e6240c2057be8af3ce36))

## [0.10.4](https://github.com/growthbook/growthbook-sdk-java/compare/0.10.3...0.10.4) (2025-11-10)


### Bug Fixes

* Optimized Sticky bucket generation ([#181](https://github.com/growthbook/growthbook-sdk-java/issues/181)) ([65f5ca9](https://github.com/growthbook/growthbook-sdk-java/commit/65f5ca97a136c33dde9daed8ff628c4f7fdaccc6))

## [0.10.3](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.2...0.10.3) (2025-11-10)


### Bug Fixes

* added manual `.builder()` method to `UserContext` to align to the documentation ([#172](https://github.com/growthbook/growthbook-sdk-java/issues/172)) ([987cb33](https://github.com/growthbook/growthbook-sdk-java/commit/987cb33c9a3a866ae7b260cfb6623b52829d208d))
* Performance and bug fixes ([#178](https://github.com/growthbook/growthbook-sdk-java/issues/178)) ([82bc58e](https://github.com/growthbook/growthbook-sdk-java/commit/82bc58e52fc1fd30b2617afbdce066e935afae38))

## [0.10.2](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.1...0.10.2) (2025-08-20)


### Bug Fixes

* ScheduledExecutorService for Feature Refresh, Cache updates and evalPath Optimization ([#168](https://github.com/growthbook/growthbook-sdk-java/issues/168)) ([be12839](https://github.com/growthbook/growthbook-sdk-java/commit/be128397ffa9e945f87ffdd298ac269a48116d9d))

## [0.10.1](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.0...0.10.1) (2025-05-12)


### Bug Fixes

* handle decryption for explicit features ([#157](https://github.com/growthbook/growthbook-sdk-java/issues/157)) ([76e5c38](https://github.com/growthbook/growthbook-sdk-java/commit/76e5c385b76eb6b6744f4a66f810288d5750a98b))

## [0.10.0](https://github.com/growthbook/growthbook-sdk-java/compare/v0.9.97...v0.10.0) (2025-04-04)


### ⚠ BREAKING CHANGES

* performance optimization & checks ([#144](https://github.com/growthbook/growthbook-sdk-java/issues/144))

### Bug Fixes

* performance optimization & checks ([#144](https://github.com/growthbook/growthbook-sdk-java/issues/144)) ([a58053f](https://github.com/growthbook/growthbook-sdk-java/commit/a58053ff62ca0a22ba5384e246338ba86a4364dc))
* release-type java-yoshi dep errors ([#138](https://github.com/growthbook/growthbook-sdk-java/issues/138)) ([6457ff0](https://github.com/growthbook/growthbook-sdk-java/commit/6457ff09e8a23ed243efceabced27d21e6880fc6))

## [0.9.97] - 2024-09-28

### Features

* Initial setup with Release-Please for automated versioning

### Bug Fixes

* Improved performance by using direct deserialization for feature objects
* Fixed memory issues with feature evaluation

### Documentation

* Added automated release documentation
