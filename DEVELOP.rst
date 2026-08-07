===============
Developer Guide
===============

Building
========

This project uses Gradle_ as build tool.

Gradle can be invoked like so::

    $ ./gradlew

The first time this command is executed, Gradle is downloaded and bootstrapped
for you automatically.

Testing
=======

Run the unit tests like so::

    $ ./gradlew test

.. _Gradle: https://gradle.org/


If you are using MacOS, you can run the unit tests locally as::

  docker run --rm --platform linux/x86_64 --user 501:0 -e HOME=/tmp -e GRADLE_USER_HOME=/tmp/.gradle -v "$PWD":/work -w /work eclipse-temurin:11-jdk bash -c './gradlew --no-daemon test'

When on M1 or later, please enable Rosetta.

Vulnerability Check
===================

Dependencies can be checked for known vulnerabilities by running::

    $ ./gradlew dependencyCheckAnalyze

Preparing a Release
===================

To create a new release, you must:

- Update ``version`` with the version to release in ``gradle.properties``

- Add a section for the new version in the ``CHANGES.txt`` file

- Commit your changes with a message like "prepare release x.y.z"

- Push changes to origin

- Create a tag by running ``git tag <x.y.z>``

- Push tag to origin by running ``git push --tags``

- Deploy to maven central (see section below)


Maven Central Deployment
========================

We deploy the artifcats via a Jenkins job, `crate_java_testing_publish`_.
Navigate to this site, and run "Build with Parameters" on the master branch, and a new release will be published to `maven central`_.

.. _crate_java_testing_publish_: https://jenkins.crate.io/job/Ecosystem/job/Release/job/Devtools/job/crate_java_testing_publish/
.. _maven_central_: https://central.sonatype.com/artifact/io.crate/crate-testing
