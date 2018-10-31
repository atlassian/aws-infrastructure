# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## API
The API consists of all public Java types from `com.atlassian.performance.tools.awsinfrastructure.api` and its subpackages:

  * [source compatibility]
  * [binary compatibility]
  * [behavioral compatibility] with behavioral contracts expressed via Javadoc

[source compatibility]: http://cr.openjdk.java.net/~darcy/OpenJdkDevGuide/OpenJdkDevelopersGuide.v0.777.html#source_compatibility
[binary compatibility]: http://cr.openjdk.java.net/~darcy/OpenJdkDevGuide/OpenJdkDevelopersGuide.v0.777.html#binary_compatibility
[behavioral compatibility]: http://cr.openjdk.java.net/~darcy/OpenJdkDevGuide/OpenJdkDevelopersGuide.v0.777.html#behavioral_compatibility

### POM
Changing the license is breaking a contract.
Adding a requirement of a major version of a dependency is breaking a contract.
Dropping a requirement of a major version of a dependency is a new contract.

## [Unreleased]
[Unreleased]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/master%0Drelease-1.3.0

### Added
- Respect `JiraNodeConfig.launchTimeouts` in `StandaloneFormula` and `DataCenterFormula` which resolves [JPERF-216]

[JPERF-216]: https://ecosystem.atlassian.net/browse/JPERF-216

## [1.3.0] - 2018-10-26
[1.3.0]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.3.0%0Drelease-1.2.1

### Added
- Chromium support. Resolve [JPERF-238].
- Provision Jira Service Desk. Resolve [JPERF-167].

[JPERF-167]: https://ecosystem.atlassian.net/browse/JPERF-167
[JPERF-238]: https://ecosystem.atlassian.net/browse/JPERF-238

## [1.2.1] - 2018-10-19
[1.2.1]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.2.1%0Drelease-1.2.0

## Fixed
- Respect the `DataCenterFormula.computer` parameter. Fix [JPERF-220].

[JPERF-220]: https://ecosystem.atlassian.net/browse/JPERF-120

## [1.2.0] - 2018-10-16
[1.2.0]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.2.0%0Drelease-1.1.1

## Added
- Support different hardware specifications for Jira nodes.
- Support fast block storage for Jira nodes. Resolve [JPERF-164] and [JPERF-120].

## Fixed
- Improve logging to find the SSH login and JPT-issued-commands more easily.
- Speed up Jira home provisioning for the `largeJira` dataset by removing the Jira backups.

## Deprecated
- Deprecate formula constructors, which don't enforce a computer choice.

[JPERF-120]: https://ecosystem.atlassian.net/browse/JPERF-120
[JPERF-164]: https://ecosystem.atlassian.net/browse/JPERF-164

## [1.1.1] - 2018-10-03
[1.1.1]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.1.1%0Drelease-1.1.0

## Fixed
- Increase the timeouts when storing custom datasets.

## [1.1.0] - 2018-09-25
[1.1.0]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.1.0%0Drelease-1.0.2

## Added
- Forward logs to Splunk from a predefined directory on Virtual User nodes.

## [1.0.2] - 2018-09-11
[1.0.2]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.0.2%0Drelease-1.0.1

## Fixed
- Increase Jira home download time for the large Jira dataset. Fix [JPERF-68](https://ecosystem.atlassian.net/browse/JPERF-68).

## [1.0.1] - 2018-09-10
[1.0.1]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.0.1%0Drelease-1.0.0

## Fixed
- Increase Jira home download time for the large Jira dataset. Fix [JPERF-68](https://ecosystem.atlassian.net/browse/JPERF-68).

## [1.0.0] - 2018-09-05
[1.0.0]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-1.0.0%0Drelease-0.1.1

### Changed
- Require APT `jira-actions:2`.
- Define the public API.
- Use stable APT APIs.

## [0.1.1] - 2018-08-28
[0.1.1]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-0.1.1%0Drelease-0.0.3

### Added
- Allow throttling virtual user diagnostics.

## [0.0.3] - 2018-08-27
[0.0.3]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-0.0.3%0Drelease-0.0.2

### Fixed
- [JPERF-26] - Increase Jira AWS Instance disk size.

[JPERF-26]: https://ecosystem.atlassian.net/browse/JPERF-26

## [0.0.2] - 2018-08-24
[0.0.2]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-0.0.2%0Drelease-0.0.1

### Added
- License.
- Add this change log.

### Fixed
- Depend on a stable version of APT `infrastructure`.

## [0.0.1] - 2018-08-03
[0.0.1]: https://bitbucket.org/atlassian/aws-infrastructure/branches/compare/release-0.0.1%0Dinitial-commit

### Added
- Migrate provisioning of JPT infrastructure in AWS from a [JPT submodule].
- Add [README.md](README.md).
- Configure Bitbucket Pipelines.

[JPT submodule]: https://stash.atlassian.com/projects/JIRASERVER/repos/jira-performance-tests/browse/aws-infrastructure?at=0b5dd43377e372ed75ccac9dd468b798b321eca5
