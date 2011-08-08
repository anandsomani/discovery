package com.proofpoint.discovery;

import com.proofpoint.cassandra.testing.CassandraServerSetup;
import com.proofpoint.node.NodeInfo;
import me.prettyprint.hector.api.Cluster;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class TestCassandraStaticStore
    extends TestStaticStore
{
    private final static AtomicLong counter = new AtomicLong(0);

    @Override
    protected StaticStore initializeStore()
    {
        CassandraStoreConfig storeConfig = new CassandraStoreConfig()
                .setKeyspace("test_cassandra_static_store" + counter.incrementAndGet());

        Cluster cluster = new DiscoveryModule().getCluster(CassandraServerSetup.getServerInfo(), new NodeInfo("testing"));

        Assert.assertTrue(new CassandraSchemaInitialization(cluster, storeConfig).waitForInit());
        final CassandraStaticStore staticStore = new CassandraStaticStore(storeConfig, cluster, new TestingTimeProvider());
 
        return new StaticStore()
        {
            @Override
            public void put(Service service)
            {
                staticStore.put(service);
            }

            @Override
            public void delete(Id<Service> nodeId)
            {
                staticStore.delete(nodeId);
            }

            @Override
            public Set<Service> getAll()
            {
                staticStore.reload();
                return staticStore.getAll();
            }

            @Override
            public Set<Service> get(String type)
            {
                staticStore.reload();
                return staticStore.get(type);
            }

            @Override
            public Set<Service> get(String type, String pool)
            {
                staticStore.reload();
                return staticStore.get(type, pool);
            }
        };
    }

    @BeforeSuite
    public void setupCassandra()
            throws IOException, TTransportException, ConfigurationException, InterruptedException
    {
        CassandraServerSetup.tryInitialize();
    }

    @AfterSuite
    public void teardownCassandra()
            throws IOException
    {
        CassandraServerSetup.tryShutdown();
    }
}
