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
[Unreleased]: https://github.com/atlassian/aws-infrastructure/compare/release-2.19.1...master

### Added
- Add `JiraSoftwareDevDistribution`. Resolve [JPERF-594].
- Expose `S3TarGzDistribution` to facilitate alternative `ProductDistribution` implementations.
  Avoid the likes of [JPERF-594] in the future.

[JPERF-594]: https://ecosystem.atlassian.net/browse/JPERF-594

## [2.19.1] - 2019-11-27
[2.19.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.19.0...release-2.19.1

### Fixed
- Include `instanceType` in copying builders of `VirtualUserFormula` implementations.

## [2.19.0] - 2019-11-22
[2.19.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.18.0...release-2.19.0

### Added
- Add `instanceType` to `StackVirtualUsersFormula` and `MulticastVirtualUsersFormula`.
  Allow avoiding low capacity instance types.

## [2.18.0] - 2019-10-17
[2.18.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.17.0...release-2.18.0

### Added
- Add `UriJiraFormula`. Resolve [JPERF-579].

[JPERF-579]: https://ecosystem.atlassian.net/browse/JPERF-579

## [2.17.0] - 2019-09-04
[2.17.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.16.0...release-2.17.0

### Deprecated
- Replace `network` method with `instanceType` in `Ec2VirtualUsersFormula.Builder`.

## [2.16.0] - 2019-09-03
[2.16.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.15.0...release-2.16.0

### Added
- Expose API to customise instance type in `Ec2VirtualUsersFormula`. Resolve [JPERF-511].
- Expose API to customise `browser` and `nodeOrder` in `Ec2VirtualUsersFormula`.

[JPERF-511]: https://ecosystem.atlassian.net/browse/JPERF-511

## [2.15.0] - 2019-09-02
[2.15.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.14.0...release-2.15.0

### Added
- Expose `AwsCli`. Provide workarounds for [JPERF-567] and the like.
- Distribute internal JSW releases via `JiraSoftwareInternalDistribution`. Resolve [JPERF-567].

### Fixed
- Ensure `S3Artifact` works even if the AWS CLI was missing. Provide a workaround for [JPERF-567].

[JPERF-567]: https://ecosystem.atlassian.net/browse/JPERF-567

## [2.14.0] - 2019-08-23
[2.14.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.13.1...release-2.14.0

### Added
- Let multicast VU nodes share a network. Resolve [JPERF-553].
- Provide a `NetworkFormula`. Facilitate [JPERF-553].

[JPERF-553]: https://ecosystem.atlassian.net/browse/JPERF-553

## [2.13.1] - 2019-07-23
[2.13.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.13.0...release-2.13.1

### Fixed
- Pass Jira and DB volume parametrization in DC and Server formulas. Fix [JPERF-534].

[JPERF-534]: https://ecosystem.atlassian.net/browse/JPERF-534

## [2.13.0] - 2019-07-11
[2.13.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.12.1...release-2.13.0

### Added
- Expose `S3DatasetPackage` to define private datasets.

## [2.12.1] - 2019-07-09
[2.12.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.12.0...release-2.12.1

### Fixed
- Parallelize AwsCli#ensureAwsCli across SSH hosts. Fix [JPERF-518].

[JPERF-518]: https://ecosystem.atlassian.net/browse/JPERF-518

## [2.12.0] - 2019-07-08
[2.12.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.11.0...release-2.12.0

### Added
- Makes ApacheEc2LoadBalancerFormula available as part of API
- Let `AwsDatasetModification` build the `Dataset` without disturbing the `InfrastructureFormula` and vice versa.
- Add `SshMysqlDatasetPublication`. Resolve [JPERF-519].
- Add `DatasetCatalogue().smallJiraSeven()`. Unblock [JPERF-214].

### Fixed
- Error while AwsDatasetModification saves dataset. Fix [JPERF-515].
- Update jira configuration if ApacheLoadBalancer is chosen. Fix [JPERF-506]
- Fix `Ec2VirtualUsersFormula` network. Fix [JPERF-406].
- Fix `StandaloneFormula` network. Fix [JPERF-516].

### Deprecated
- Replace `AwsDatasetModification.formula` with a pair of `dataset` and `host`.

[JPERF-519]: https://ecosystem.atlassian.net/browse/JPERF-519
[JPERF-214]: https://ecosystem.atlassian.net/browse/JPERF-214
[JPERF-515]: https://ecosystem.atlassian.net/browse/JPERF-515
[JPERF-506]: https://ecosystem.atlassian.net/browse/JPERF-506
[JPERF-406]: https://ecosystem.atlassian.net/browse/JPERF-406
[JPERF-516]: https://ecosystem.atlassian.net/browse/JPERF-516

## [2.11.0] - 2019-07-01
[2.11.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.10.1...release-2.11.0

### Added
- Add `AwsDatasetModification`. Resolve [JPERF-509].

[JPERF-509]: https://ecosystem.atlassian.net/browse/JPERF-509

## [2.10.1] - 2019-06-24
[2.10.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.10.0...release-2.10.1

### Fixed
- Configure Ehcache listener. Fix [JPERF-503].

[JPERF-503]: https://ecosystem.atlassian.net/browse/JPERF-503
## [2.10.0] - 2019-06-21
[2.10.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.9.0...release-2.10.0

### Added
- Add the parameter for specifying volume size. Resolve [JPERF-26].

[JPERF-26]: https://ecosystem.atlassian.net/browse/JPERF-26

## [2.9.0] - 2019-05-28
[2.9.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.8.0...release-2.9.0

### Added
- Add support for `infrastructure:4.12`.
- Add support for Apache proxy load balancer.

### Fixed
- Make `Ubuntu` thread safe. Resolve [JPERF-468].
- Fixed unequal VU distribution amongst jira nodes. Resolve [JPERF-486].

[JPERF-468]: https://ecosystem.atlassian.net/browse/JPERF-468
[JPERF-486]: https://ecosystem.atlassian.net/browse/JPERF-486

## [2.8.0] - 2019-04-17
[2.8.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.7.3...release-2.8.0

### Added
- Give more control over `VirtualUserOptions`. Resolve [JPERF-451].

### Deprecated
- Deprecate the hardcoded admin credentials in `Infrastructure.applyLoad`.

### Fixed
- Increase timeout for creating the filesystem on ephemeral storage. Resolve [JPERF-433].
- Install Jira and download dataset at the same time. Fix [JPERF-457].

[JPERF-451]: https://ecosystem.atlassian.net/browse/JPERF-451
[JPERF-457]: https://ecosystem.atlassian.net/browse/JPERF-457

## [2.7.3] - 2019-03-28
[2.7.3]: https://github.com/atlassian/aws-infrastructure/compare/release-2.7.2...release-2.7.3

### Fixed
- Increase timeout for creating the filesystem on ephemeral storage. The timeout was too small to 
resolve [JPERF-433].

[JPERF-433]: https://ecosystem.atlassian.net/browse/JPERF-433

## [2.7.2] - 2019-03-27
[2.7.2]: https://github.com/atlassian/aws-infrastructure/compare/release-2.7.1...release-2.7.2

### Fixed
- Fix duplicated virtual user node order. Fix [JPERF-431].

[JPERF-431]: https://ecosystem.atlassian.net/browse/JPERF-431

## [2.7.1] - 2019-03-19
[2.7.1]: https://github.com/atlassian/aws-infrastructure/compare/release-2.7.0...release-2.7.1

### Fixed
- Copy the `databaseComputer` in Jira formula builders.

## [2.7.0] - 2019-03-07 
[2.7.0]: https://github.com/atlassian/aws-infrastructure/compare/release-2.6.0...release-2.7.0

### Added
- Gather Thread Dumps during Jira startup.
- Add support for `infrastructure:4.10`.
- Add support for configurable database's `Computer`. Resolve [JPERF-414].
- Add support for `M4ExtraLargeElastic` computer.
- Add support for `M5ExtraLargeEphemeral` computer.

### Fixed
- Gather Jira logs even if the node doesn't start in time. Resolve [JPERF-404].
- Download and unzip datasets in parallel. Resolve [JPERF-412] and [JPERF-413].
- Utilise ephemeral drive for shared home if provided. Resolve [JPERF-415].

[JPERF-404]: https://ecosystem.atlassian.net/browse/JPERF-404
[JPERF-412]: https://ecosystem.atlassian.net/browse/JPERF-412
[JPERF-413]: https://ecosystem.atlassian.net/browse/JPERF-413
[JPERF-414]: https://ecosystem.atlassian.net/browse/JPERF-414
[JPERF-415]: https://ecosystem.atlassian.net/browse/JPERF-415

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

[JPERF-318]: https://ecosystem.atlassian.net/browse/JPERF-318

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
