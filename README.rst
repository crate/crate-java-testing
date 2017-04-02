============================
CrateDB Java Testing Classes
============================

This project provides the ``CrateTestServer`` and ``CrateTestCluster`` classes
for use as `JUnit external resources`_.

Both classes download and start CrateDB before test execution and stop CrateDB
afterwards.

Setup
=====

JAR files are hosted on `Bintray`_ and available via `JCenter`_.

If you want to use ``crate-testing`` with your Maven project, add the Bintray
repository to your ``pom.xml``:

.. code-block:: xml

    ...
    <repositories>
        ...
        <repository>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
            <id>central</id>
            <name>bintray</name>
            <url>http://dl.bintray.com/crate/crate</url>
        </repository>
    </repositories>
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>io.crate</groupId>
            <artifactId>crate-testing</artifactId>
            <version>...</version>
        </dependency>
    </dependencies>
    ...

You can integrate with Gradle like so:

.. code-block:: groovy

    repositories {
        ...
        jcenter()
    }

    dependencies {
        compile 'io.crate:crate-testing:...'
        ...
    }

Alternatively you can follow the instructions on the Bintray repository
`overview page`_ by selecting the *Set me up!* button.

``CrateTestCluster``
===================

The ``CrateTestCluster`` class is ``io.crate.testing.CrateTestCluster`` and
extends ``org.junit.rules.ExternalResources``.

Once it's initalized as a JUnit ``ClassRule``, ``CrateTestCluster`` will start
CrateDB at the beginning of the test run. If CrateDB is not already downloaded,
it will be downloaded and extracted.

To start ``CrateTestCluster``, use static factory methods ``fromURL``,
``fromFile``, ``fromSysProperties`` or ``fromVersion``.

Example usage in a Java test:

.. code-block:: java

    @ClassRule
    public static final CrateTestCluster TEST_CLUSTER =
        CrateTestCluster.fromVersion("0.52.4")
        .clusterName("with-builder")
        .numberOfNodes(3)
        .build();

When using the ``fromSysProperties`` static factory method, either the
``crate.testing.from_version`` or ``crate.testing.from_url`` system property
must be set. If both system properties are provided, the
``crate.testing.from_version`` property is used.

Contributing
============

This project is primarily maintained by `Crate.io`_, but we welcome community
contributions!

See the `developer docs`_ and the `contribution docs`_ for more information.

Help
====

Looking for more help?

- Check `StackOverflow`_ for common problems
- Chat with us on `Slack`_
- Get `paid support`_

.. _Bintray: https://bintray.com/crate/crate/
.. _contribution docs: CONTRIBUTING.rst
.. _Crate.io: http://crate.io/
.. _developer docs: DEVELOP.rst
.. _JCenter: https://bintray.com/bintray/jcenter
.. _JUnit external resources:  https://github.com/junit-team/junit/wiki/Rules#externalresource-rules
.. _overview page: https://bintray.com/crate/crate/
.. _paid support: https://crate.io/pricing/
.. _Slack: https://crate.io/docs/support/slackin/
.. _StackOverflow: https://stackoverflow.com/tags/crate
