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

The artifacts can be uploaded to maven central using ``./gradlew uploadArchives closeAndReleaseRepository``.
This gradle task requires signing and sonatype credentials.
