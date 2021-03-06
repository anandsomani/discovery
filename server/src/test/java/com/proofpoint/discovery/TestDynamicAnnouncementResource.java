package com.proofpoint.discovery;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.discovery.event.DiscoveryEventConfig;
import com.proofpoint.discovery.event.DiscoveryEvents;
import com.proofpoint.event.client.InMemoryEventClient;
import com.proofpoint.jaxrs.testing.MockUriInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;

import static com.google.common.collect.Iterables.transform;
import static com.proofpoint.discovery.DynamicServiceAnnouncement.toServiceWith;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestDynamicAnnouncementResource
{
    private InMemoryDynamicStore store;
    private DynamicAnnouncementResource resource;
    private DiscoveryConfig discoveryConfig;

    @BeforeMethod
    public void setup()
    {
        store = new InMemoryDynamicStore(new DiscoveryConfig(), new RealTimeProvider());
        discoveryConfig = new DiscoveryConfig();
        resource = new DynamicAnnouncementResource(store, new NodeInfo("testing"), new DiscoveryEvents(new InMemoryEventClient(), new DiscoveryEventConfig().setEnabledEvents("")), discoveryConfig);
    }

    @Test
    public void testPutNew()
    {
        DynamicAnnouncement announcement = new DynamicAnnouncement("testing", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("http", "http://localhost:1111")))
        );

        Id<Node> nodeId = Id.random();
        Response response = resource.put(nodeId, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/" + nodeId.toString())), announcement, null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        assertEqualsIgnoreOrder(store.getAll(), transform(announcement.getServiceAnnouncements(), toServiceWith(nodeId, announcement.getLocation(), announcement.getPool())));
        assertEquals(resource.getDynamicDeleteStats().getCount(), 0);
        assertEquals(resource.getDynamicPutStats().getCount(), 1);
        assertEquals(resource.getEnvironmentMismatchCount(),0);
        assertEquals(resource.getNotFoundCount(),0);
    }

    @Test
    public void testReplace()
    {
        Id<Node> nodeId = Id.random();
        DynamicAnnouncement previous = new DynamicAnnouncement("testing", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("key", "existing"))
        ));

        store.put(nodeId, previous);

        DynamicAnnouncement announcement = new DynamicAnnouncement("testing", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("key", "new")))
        );

        Response response = resource.put(nodeId, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/" + nodeId.toString())), announcement, null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        assertEqualsIgnoreOrder(store.getAll(), transform(announcement.getServiceAnnouncements(), toServiceWith(nodeId, announcement.getLocation(), announcement.getPool())));
        assertEquals(resource.getDynamicDeleteStats().getCount(), 0);
        assertEquals(resource.getDynamicPutStats().getCount(), 1);
        assertEquals(resource.getEnvironmentMismatchCount(),0);
        assertEquals(resource.getNotFoundCount(),0);
    }

    @Test
    public void testEnvironmentConflict()
    {
        DynamicAnnouncement announcement = new DynamicAnnouncement("production", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("http", "http://localhost:1111")))
        );

        Id<Node> nodeId = Id.random();
        Response response = resource.put(nodeId, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/" + nodeId.toString())), announcement, null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());

        assertTrue(store.getAll().isEmpty());
        assertEquals(resource.getDynamicDeleteStats().getCount(), 0);
        assertEquals(resource.getDynamicPutStats().getCount(), 0);
        assertEquals(resource.getEnvironmentMismatchCount(),1);
        assertEquals(resource.getNotFoundCount(),0);
    }

    @Test
    public void testDeleteExisting()
    {
        Id<Node> blueNodeId = Id.random();
        DynamicAnnouncement blue = new DynamicAnnouncement("testing", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("key", "valueBlue"))
        ));

        Id<Node> redNodeId = Id.random();
        DynamicAnnouncement red = new DynamicAnnouncement("testing", "alpha", "/a/b/c", ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("key", "valueBlue"))
        ));

        store.put(redNodeId, red);
        store.put(blueNodeId, blue);

        Response response = resource.delete(blueNodeId, null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

        assertEqualsIgnoreOrder(store.getAll(), transform(red.getServiceAnnouncements(), toServiceWith(redNodeId, red.getLocation(), red.getPool())));
        assertEquals(resource.getDynamicDeleteStats().getCount(), 1);
        assertEquals(resource.getDynamicPutStats().getCount(), 0);
        assertEquals(resource.getEnvironmentMismatchCount(),0);
        assertEquals(resource.getNotFoundCount(),0);
    }

    @Test
    public void testDeleteMissing()
    {
        Response response = resource.delete(Id.<Node>random(), null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());

        assertTrue(store.getAll().isEmpty());
        assertEquals(resource.getDynamicDeleteStats().getCount(), 0);
        assertEquals(resource.getDynamicPutStats().getCount(), 0);
        assertEquals(resource.getEnvironmentMismatchCount(),0);
        assertEquals(resource.getNotFoundCount(),1);
    }

    @Test
    public void testMakesUpLocation()
    {
        DynamicAnnouncement announcement = new DynamicAnnouncement("testing", "alpha", null, ImmutableSet.of(
                new DynamicServiceAnnouncement(Id.<Service>random(), "storage", ImmutableMap.of("http", "http://localhost:1111")))
        );

        Id<Node> nodeId = Id.random();
        Response response = resource.put(nodeId, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/" + nodeId.toString())), announcement, null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        assertEquals(store.getAll().size(), 1);
        Service service = store.getAll().iterator().next();
        assertEquals(service.getId(), service.getId());
        assertNotNull(service.getLocation());
    }
}
