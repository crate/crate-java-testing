.. image:: https://cdn.crate.io/web/2.0/img/crate-avatar_100x100.png
    :width: 100px
    :height: 100px
    :alt: Crate.IO
    :target: https://crate.io

==================
Crate Java Testing
==================

The ``crate-java-testing`` project contains a CrateTestServer and CrateTestCluster
which are usable as `JUnit external resource`_.

Both classes download and start Crate before test execution and stop it afterwards.


Download and Setup
==================

The ``crate-testing`` jar files are hosted on `Bintray`_ and available via `JCenter`_.

If you want to use ``crate-testing`` with your Maven project you need to
add the Bintray repository to your ``pom.xml``:

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


Using Gradle:

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
overview page by clicking the "Set me up!" button.


CrateTestServer Class
=====================

The Crate Test Server class is ``io.crate.testing.CrateTestCluster``.
``CrateTestCluster`` extends from ``org.junit.rules.ExternalResources``.
Once it's initalized as a junit ``ClassRule`` it will start Crate at the
beginning of the test run. If crate is not already downloaded, it will be
downloaded and extracted. In order to start  ``CrateTestCluster`` use static
factory methods ``fromURL``,  ``fromFile``, ``fromSysProperties`` or
``fromVersion``. This makes it possible to configure the server in many ways::

Example usage in a java test::

    @ClassRule
    public static final CrateTestCluster TEST_CLUSTER = CrateTestCluster.fromVersion("0.52.4")
                                                                        .clusterName("with-builder")
                                                                        .numberOfNodes(3)
                                                                        .build();

or simply use one of the static methods. The cluster name will be generated
and the default number of nodes, which is equals 1, will be used::

    @ClassRule
    public static final CrateTestCluster TEST_CLUSTER = CrateTestCluster.fromVersion("0.52.2")
                                                                       .build();

When using ``fromSysProperties`` static factory method, either
``crate.testing.from_version`` or ``crate.testing.from_url`` system property
must be set. If both system properties are provided, then the
``crate.testing.from_version`` property is used.


.. _`Bintray`: https://bintray.com/crate/crate/

.. _`JCenter`: https://bintray.com/bintray/jcenter

.. _`JUnit external resource`:  https://github.com/junit-team/junit/wiki/Rules#externalresource-rules
