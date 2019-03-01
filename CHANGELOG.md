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
[Unreleased]: https://github.com/atlassian/aws-infrastructure/compare/release-2.6.0...master

### Fixed
- Gather Jira logs even if the node doesn't start in time. Reslove [JPERF-404].

[JPERF-404]: https://ecosystem.atlassian.net/browse/JPERF-404

## [2.6.0] - 2019-02-28 
[2.6.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.5.1...release-2.6.0

### Added
- Provision a shared network for `InfrastructureFormula`. Progress on [JPERF-357]. Override it in:
    - `DataCenterFormula`
    - `StandaloneFormula`
    - `StackVirtualUsersFormula`
    - `Ec2VirtualUsersFormula`
    - `MulticastVirtualUsersFormula`
- Allow usage of different artifact sources than S3. Resolves [JPERF-277].

### Deprecated
- Deprecate `Ec2VirtualUsersFormula` constructors in favor of the builder.
- Deprecate `DataCenterFormula.Builder` constructor in favor of the new one.
- Deprecate `StandaloneFormula.Builder` constructor in favor of the new one.
- Deprecate `ApplicationStorage` constructor in favor of `ProductDistribution`.
- Deprecate `JiraServiceDeskStorage` constructor in favor of `PublicJiraServiceDeskDistribution`.
- Deprecate `JiraServiceDeskStorage` constructor in favor of `PublicJiraSoftwareDistribution`.

[JPERF-357]: https://ecosystem.atlassian.net/browse/JPERF-357
[JPERF-277]: https://ecosystem.atlassian.net/browse/JPERF-277

## [2.5.1] - 2019-01-18
[2.5.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.5.0...release-2.5.1

### Fixed
- Attempt to download MySQL connector multiple times to make this procedure more reliable. Resolve [JPERF-342].

[JPERF-342]: https://ecosystem.atlassian.net/browse/JPERF-342

## [2.5.0] - 2019-01-07
[2.5.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.4.0...release-2.5.0

### Added
- Add an option to customise stack creation timeout for virtual users. Resolve [JPERF-336]

### Deprecated
- Deprecate `StackVirtualUsers` constructors in favour of builder.

[JPERF-336]:https://ecosystem.atlassian.net/browse/JPERF-336

## [2.4.0] - 2019-01-03
[2.4.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.3.0...release-2.4.0

### Added
- Add an option to customise stack creation timeout for Jira. Resolve [JPERF-332]

[JPERF-332]:https://ecosystem.atlassian.net/browse/JPERF-332

## [2.3.0] - 2018-12-21
[2.3.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.2.0...release-2.3.0

### Added
- Add profiler support. Resolve [JPERF-318].

[JPERF-318]:https://ecosystem.atlassian.net/browse/JPERF-318

## [2.2.0] - 2018-12-18
[2.2.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.1.1...release-2.2.0

### Added
- Customise JDK Jira runs on. Resolve [JPERF-305].

[JPERF-305]: https://ecosystem.atlassian.net/browse/JPERF-305

### Added
- Add `DatasetCatalogue.largeJiraSeven` as a replacement for `DatasetCatalogue.largeJira`.
- Add `DatasetCatalogue.largeJiraEight` dataset for Jira 8 which is necessary for [JPERF-307].

## Deprecated
- Deprecate `DatasetCatalogue.largeJira`.

[JPERF-307]: https://ecosystem.atlassian.net/browse/JPERF-307

## [2.1.1] - 2018-12-06 🎅
[2.1.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.1.0...release-2.1.1

### Fixed
- Provision stacks in dedicated VPCs. Resolve [JPERF-292].

[JPERF-292]: https://ecosystem.atlassian.net/browse/JPERF-292

## [2.1.0] - 2018-12-06
[2.1.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.0.0...release-2.1.0

### Added
- Add support for `jira-actions:3`. Resolve [JPERF-297].

[JPERF-297]: https://ecosystem.atlassian.net/browse/JPERF-297

## [2.0.0] - 2018-11-28
[2.0.0]: https://github.com/atlassian/aws-infrastructure/compare/release-1.4.0...release-2.0.0

### Added
- Add support for other EC2 instance types. Resolve [JPERF-276].
- Add support for `infrastructure:4`.
- Add support for `ssh:2`.
- Add support for `virtual-users:3`.
- Respect the `JiraLaunchTimeouts.unresponsivenessTimeout` in `StandaloneStoppedNode`. Resolve [JPERF-271]. 

### Fixed
- Reduce apt-get usage in `AwsCli`. Mitigate [JPERF-219].
- Increase awscli-bundle install timeout. Fix [JPERF-266].
- Cease to rewrite `VirtualUserOptions` parameters, allowing `aws-infrastructure` to forward new parameters
  without releasing new rewrite code every time `virtual-users` releases a new parameter. Resolve [JPERF-252].

### Removed
- Drop support for `infrastructure:2`.
- Drop support for `ssh:1`.
- Drop support for `virtual-users:2`.
- Remove Kotlin data-class generated methods from API.
- Remove all deprecated API.

[JPERF-219]: https://ecosystem.atlassian.net/browse/JPERF-219
[JPERF-252]: https://ecosystem.atlassian.net/browse/JPERF-252
[JPERF-266]: https://ecosystem.atlassian.net/browse/JPERF-266
[JPERF-271]: https://ecosystem.atlassian.net/browse/JPERF-271
[JPERF-276]: https://ecosystem.atlassian.net/browse/JPERF-276

## [1.4.0] - 2018-10-31
[1.4.0]: https://github.com/atlassian/aws-infrastructure/compare/release-1.3.0...release-1.4.0

### Added
- Respect `JiraNodeConfig.launchTimeouts` in `StandaloneFormula` and `DataCenterFormula`. Resolve [JPERF-216].

[JPERF-216]: https://ecosystem.atlassian.net/browse/JPERF-216

## 1.3.1 - 2018-10-31
It was a botched 1.4.0 release. Don't use new APIs from this version, switch to 1.4.0 instead.

## [1.3.0] - 2018-10-26
[1.3.0]: https://github.com/atlassian/aws-infrastructure/compare/release-1.2.1...release-1.3.0

### Added
- Chromium support. Resolve [JPERF-238].
- Provision Jira Service Desk. Resolve [JPERF-167].

[JPERF-167]: https://ecosystem.atlassian.net/browse/JPERF-167
[JPERF-238]: https://ecosystem.atlassian.net/browse/JPERF-238

## [1.2.1] - 2018-10-19
[1.2.1]: https://github.com/atlassian/aws-infrastructure/compare/release-1.2.0...release-1.2.1

## Fixed
- Respect the `DataCenterFormula.computer` parameter. Fix [JPERF-220].

[JPERF-220]: https://ecosystem.atlassian.net/browse/JPERF-120

## [1.2.0] - 2018-10-16
[1.2.0]: https://github.com/atlassian/aws-infrastructure/compare/release-1.1.1...release-1.2.0

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
[1.1.1]: https://github.com/atlassian/aws-infrastructure/compare/release-1.1.0...release-1.1.1

## Fixed
- Increase the timeouts when storing custom datasets.

## [1.1.0] - 2018-09-25
[1.1.0]: https://github.com/atlassian/aws-infrastructure/compare/release-1.0.2...release-1.1.0

## Added
- Forward logs to Splunk from a predefined directory on Virtual User nodes.

## [1.0.2] - 2018-09-11
[1.0.2]: https://github.com/atlassian/aws-infrastructure/compare/release-1.0.1...release-1.0.2

## Fixed
- Increase Jira home download time for the large Jira dataset. Fix [JPERF-68](https://ecosystem.atlassian.net/browse/JPERF-68).

## [1.0.1] - 2018-09-10
[1.0.1]: https://github.com/atlassian/aws-infrastructure/compare/release-1.0.0...release-1.0.1

## Fixed
- Increase Jira home download time for the large Jira dataset. Fix [JPERF-68](https://ecosystem.atlassian.net/browse/JPERF-68).

## [1.0.0] - 2018-09-05
[1.0.0]: https://github.com/atlassian/aws-infrastructure/compare/release-0.1.1...release-1.0.0

### Changed
- Require APT `jira-actions:2`.
- Define the public API.
- Use stable APT APIs.

## [0.1.1] - 2018-08-28
[0.1.1]: https://github.com/atlassian/aws-infrastructure/compare/release-0.0.3...release-0.1.1

### Added
- Allow throttling virtual user diagnostics.

## [0.0.3] - 2018-08-27
[0.0.3]: https://github.com/atlassian/aws-infrastructure/compare/release-0.0.2...release-0.0.3

### Fixed
- [JPERF-26] - Increase Jira AWS Instance disk size.

[JPERF-26]: https://ecosystem.atlassian.net/browse/JPERF-26

## [0.0.2] - 2018-08-24
[0.0.2]: https://github.com/atlassian/aws-infrastructure/compare/release-0.0.1...release-0.0.2

### Added
- License.
- Add this change log.

### Fixed
- Depend on a stable version of APT `infrastructure`.

## [0.0.1] - 2018-08-03
[0.0.1]: https://github.com/atlassian/aws-infrastructure/compare/initial-commit...release-0.0.1

### Added
- Migrate provisioning of JPT infrastructure in AWS from a [JPT submodule].
- Add [README.md](README.md).
- Configure Bitbucket Pipelines.

[JPT submodule]: https://stash.atlassian.com/projects/JIRASERVER/repos/jira-performance-tests/browse/aws-infrastructure?at=0b5dd43377e372ed75ccac9dd468b798b321eca5
