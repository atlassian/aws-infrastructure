# Contributing

Thanks for taking the time to contribute! 

The following is a set of guidelines for contributing to [aws-infrastructure](README.md).
All the changes are welcome. Please help us to improve code, examples and documentation.

## Testing 

### Unit tests

```
./gradlew test
```

### Integration tests

```
./gradlew testIntegration
```

Integration tests cover the critical features of the API.
These tests use real dependencies instead of mocking them, so they require AWS credentials.
    
## Developer’s environment

You can build and run aws-infrastructure on MacOS, Windows or Linux. You'll need JDK 8-11 to build and test the project.

## Submitting changes
 
Pull requests, issues and comments are welcome. For pull requests:

  - Create your own [fork] of the repository and raise a pull request targeting master branch in the main repository
  - Add tests for new features and bug fixes
  - Follow the existing style
  - Separate unrelated changes into multiple pull requests

See the [existing issues](https://ecosystem.atlassian.net/projects/JPERF/issues/?filter=allissues) for things to start contributing.

For bigger changes, make sure you start a discussion first by creating
an issue and explaining the intended change.

All the pull requests and other changes will be accepted and merged by Atlassians.

Atlassian requires contributors to sign a Contributor License Agreement,
known as a CLA. This serves as a record stating that the contributor is
entitled to contribute the code/documentation/translation to the project
and is willing to have it used in distributions and derivative works
(or is willing to transfer ownership).

Prior to accepting your contributions we ask that you please follow the appropriate
link below to digitally sign the CLA. The Corporate CLA is for those who are
contributing as a member of an organization and the individual CLA is for
those contributing as an individual.

* [CLA for corporate contributors](https://opensource.atlassian.com/corporate)
* [CLA for individuals](https://opensource.atlassian.com/individual)

### Pull Request Checks

Pull requests must pass a [CircleCI](https://circleci.com) build. This is **not** automatically run under Atlassian's account, as contributor you will need to setup and run the CI build against your own fork.

The CI build runs the IT tests which require your AWS credentials. At a minimum this will require the following Environent Variables to be set against the project in CircleCI:

* AWS_ACCESS_KEY_ID
* AWS_SECRET_ACCESS_KEY

If you are using session based credentials you will also need the following Environment Variable:

* AWS_SESSION_TOKEN

## Style Guide / Coding conventions

[Git commit messages](https://chris.beams.io/posts/git-commit/)

## Releasing

Versioning, releasing and distribution are managed by the [gradle-release] plugin.

[gradle-release]: https://bitbucket.org/atlassian/gradle-release/src/release-0.5.0/README.md