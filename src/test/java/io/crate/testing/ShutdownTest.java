import io.crate.testing.CrateTestCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class ShutdownTest {
    @Test
    public void test() throws Throwable {
        CrateTestCluster testCluster = CrateTestCluster.fromURL("https://cdn.crate.io/downloads/releases/nightly/crate-latest.tar.gz")
            .keepWorkingDir(false)
            .build();
        testCluster.before();
        assertThat(testCluster.isAlive(), is(true));
        testCluster.after();
        assertThat(testCluster.isAlive(), is(false));
    }
}
