============================
CrateDB Java Testing Classes
============================

``CrateTestServer`` and ``CrateTestCluster`` classes for use as `JUnit external
resources`_.

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
            <id>bintray</id>
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
        CrateTestCluster.fromVersion("3.3.2")
        .clusterName("with-builder")
        .numberOfNodes(3)
        .build();

When using the ``fromSysProperties`` static factory method, either the
``crate.testing.from_version`` or ``crate.testing.from_url`` system property
must be set. If both system properties are provided, the
``crate.testing.from_version`` property is used.

Contributing
============

This project is primarily maintained by Crate.io_, but we welcome community
contributions!

See the `developer docs`_ and the `contribution docs`_ for more information.

Help
====

Looking for more help?

- Check out our `support channels`_

.. _Bintray: https://bintray.com/crate/crate/
.. _contribution docs: CONTRIBUTING.rst
.. _Crate.io: http://crate.io/
.. _developer docs: DEVELOP.rst
.. _JCenter: https://bintray.com/bintray/jcenter
.. _JUnit external resources:  https://github.com/junit-team/junit/wiki/Rules#externalresource-rules
.. _overview page: https://bintray.com/crate/crate/
.. _support channels: https://crate.io/support/
