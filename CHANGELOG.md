# Changelog

## [0.11.0](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.10...0.11.0) (2026-08-04)


### ⚠ BREAKING CHANGES

* update OkHttp dependencies to version 5.4.0 ([#218](https://github.com/growthbook/growthbook-sdk-java/issues/218))

### Features

* add Caffeine cache adapter module ([#220](https://github.com/growthbook/growthbook-sdk-java/issues/220)) ([bee3e01](https://github.com/growthbook/growthbook-sdk-java/commit/bee3e010ac7b4a2be4c9df59ddbdda9c3470d44d))
* add configuration validation at client start-up ([#223](https://github.com/growthbook/growthbook-sdk-java/issues/223)) ([f0fb1ae](https://github.com/growthbook/growthbook-sdk-java/commit/f0fb1aecbfdf8f713a39b6e8ca0c44b6f9eda3c2))
* add custom fields ([#217](https://github.com/growthbook/growthbook-sdk-java/issues/217)) ([06c2b98](https://github.com/growthbook/growthbook-sdk-java/commit/06c2b98b68fd37695efc5ad9bd9fdf870707ff94))
* add diagnostics API ([#219](https://github.com/growthbook/growthbook-sdk-java/issues/219)) ([32925a6](https://github.com/growthbook/growthbook-sdk-java/commit/32925a6b9140808197424eb2aa26ce73d899ae3b))
* add JCache (JSR-107) cache adapter module ([#221](https://github.com/growthbook/growthbook-sdk-java/issues/221)) ([621121e](https://github.com/growthbook/growthbook-sdk-java/commit/621121e4d2fc84e2efa11213727601dc3e3cb044))
* add remote evaluation support ([#216](https://github.com/growthbook/growthbook-sdk-java/issues/216)) ([393fdc8](https://github.com/growthbook/growthbook-sdk-java/commit/393fdc8fd4eec5f346556192fe19ad936905ab7d))
* Typed feature access ([#224](https://github.com/growthbook/growthbook-sdk-java/issues/224)) ([45c57b4](https://github.com/growthbook/growthbook-sdk-java/commit/45c57b45fec74427c4acbae0e5de296d033e5d50))


### Bug Fixes

* apply 200 response payload when x-sse-support header is absent ([#231](https://github.com/growthbook/growthbook-sdk-java/issues/231)) ([e1e3b13](https://github.com/growthbook/growthbook-sdk-java/commit/e1e3b130fcf3abbe87c38c5e594f8f7141325407))
* correct sticky bucketing logic in single-user and multi-user modes ([#211](https://github.com/growthbook/growthbook-sdk-java/issues/211)) ([1ffd7dc](https://github.com/growthbook/growthbook-sdk-java/commit/1ffd7dc30de0fc5e2e56c80cdb69d9ff746aeda1))
* handle double forced variation values ([#213](https://github.com/growthbook/growthbook-sdk-java/issues/213)) ([0e5cb83](https://github.com/growthbook/growthbook-sdk-java/commit/0e5cb83fcb1126e7cdf4d6bf5fdf21bb37187a6f))
* prevent unhandled exceptions when processing empty SSE event payloads ([#214](https://github.com/growthbook/growthbook-sdk-java/issues/214)) ([c509792](https://github.com/growthbook/growthbook-sdk-java/commit/c509792a3492ac443f3685b07de7fcff8dbcbf24))
* update OkHttp dependencies to version 5.4.0 ([#218](https://github.com/growthbook/growthbook-sdk-java/issues/218)) ([72bb348](https://github.com/growthbook/growthbook-sdk-java/commit/72bb348765dc10cca78710dd1e1e6cd487ad4808))

## [0.10.10](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.9...0.10.10) (2026-05-08)


### Bug Fixes

* remove duplicate rollout check in force rule evaluation ([#208](https://github.com/growthbook/growthbook-sdk-java/issues/208)) ([4f76450](https://github.com/growthbook/growthbook-sdk-java/commit/4f764505f8581009f9828b5ad3a0e50c455de146))

## [0.10.9](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.8...0.10.9) (2026-04-07)


### Features

* add case-insensitive operators ini, nini, alli to condition evaluator ([#194](https://github.com/growthbook/growthbook-sdk-java/issues/194)) ([5e9b94c](https://github.com/growthbook/growthbook-sdk-java/commit/5e9b94cb831687f3147237157bcc76ef5cab11a6))

## [0.10.8](https://github.com/growthbook/growthbook-sdk-java/compare/0.10.7...0.10.8) (2026-03-30)


### Performance Improvements

* Reduce allocations in evalPath ([#199](https://github.com/growthbook/growthbook-sdk-java/issues/199)) ([44b08d9](https://github.com/growthbook/growthbook-sdk-java/commit/44b08d9d2f91f868b78a5ee5c0e28b9d36ae4ac2))
  - Remove merged-map allocation from forced feature & variation lookups
  - Normalize `UserContext` defaults
  - Avoid eager string work in override logging

## [0.10.7](https://github.com/growthbook/growthbook-sdk-java/compare/v0.10.6...0.10.7) (2026-03-17)


### Bug Fixes

* isOn() returns true for map/object and other missing types ([#195](https://github.com/growthbook/growthbook-sdk-java/issues/195)) ([30bf5e2](https://github.com/growthbook/growthbook-sdk-java/commit/30bf5e2fd03a498015aa82ac6f9707e60634d8c9))

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
