.. image:: https://cdn.crate.io/web/2.0/img/crate-avatar_100x100.png
    :width: 100px
    :height: 100px
    :alt: Crate.IO
    :target: https://crate.io

==================
Crate Java Testing
==================

The ``crate-java-testing`` project contains a `JUnit`_ external ressource which downloads
and starts Crate before test execution and stops it afterwards.


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

Alternatively you can follow the instructions on the Bintray repository overview page by clicking the "Set me up!" button.


CrateTestServer Class
=====================

The Crate Test Server class is ``io.crate.testing.CrateTestServer``. ``CrateTestServer`` extends
from ``org.junit.rules.ExternalResources``.
Once it's initalized as a junit ``ClassRule`` it will start Crate at the beginning of the
test run. If crate is not already downloaded, it will be downloaded and extracted. It's required
to provide at least a ``cluster name`` and the crate version as a string. If the
``cluster name`` is null, a cluster name will be generated.

Example usage in a java test::

    @ClassRule
    public static final CrateTestServer testServer = new CrateTestServer("crate-test-cluster", "0.52.2");


    @Test
    public void testSimpleTest() {
        testServer.execute("create table test (foo string)");
        testServer.execute("insert into test (foo) values ('bar')");
        testServer.execute("refresh table test");
        SQLResponse response = testServer.execute("select * from test");
        assertThat(response.rowCount(), is(1L));
    }

By using the following constructor it's also possible to provide the ``http port``,
the ``transport port``, the ``workingDirectory``, the ``host`` and a list of 
``unicast hosts``::

    public CrateTestServer(@Nullable String clusterName, String crateVersion, int httpPort, int transportPort,
                           String workingDir, String host, String ... unicastHosts) {
                           ...
                           }

Setting up a Cluster
====================

It's also possible to set up a crate cluster by using the static cluster method,
a ``cluster name``, the crate version and the number of nodes must be provided.
An array of ``CrateTestServer`` is returned, e.g.::
    
    CrateTestServer[] servers = CrateTestServer.cluster("myCluster", "0.52.2", 3)

Issue an SQL Request
====================

To issue an sql request, the ``CrateTestServer`` provides the following methods::

       public SQLResponse execute(String statement)

       public SQLResponse execute(String statement, Object[] args)


.. _`Bintray`: https://bintray.com/crate/crate/

.. _`JCenter`: https://bintray.com/bintray/jcenter

.. _`JUnit`:  http://junit.org
