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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestStaticAnnouncementResource
{
    private InMemoryStaticStore store;
    private StaticAnnouncementResource resource;
    private DiscoveryConfig discoveryConfig;

    @BeforeMethod
    public void setup()
    {
        store = new InMemoryStaticStore();
        discoveryConfig = new DiscoveryConfig();
        resource = new StaticAnnouncementResource(store, new NodeInfo("testing"), new DiscoveryEvents (new InMemoryEventClient(), new DiscoveryEventConfig().setEnabledEvents("")), discoveryConfig);
    }

    @Test
    public void testPost()
    {
        StaticAnnouncement announcement = new StaticAnnouncement("testing", "storage", "alpha", "/a/b", ImmutableMap.of("http", "http://localhost:1111"));

        Response response = resource.post(announcement, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/static")), null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

        assertEquals(store.getAll().size(), 1);
        Service service = store.getAll().iterator().next();

        assertNotNull(service.getId());
        assertNull(service.getNodeId());
        assertEquals(service.getLocation(), announcement.getLocation());
        assertEquals(service.getType(), announcement.getType());
        assertEquals(service.getPool(), announcement.getPool());
        assertEquals(service.getProperties(), announcement.getProperties());
        assertEquals(resource.getStaticGetStats().getCount(),0);
        assertEquals(resource.getStaticDeleteStats().getCount(),0);
        assertEquals(resource.getStaticPostStats().getCount(),1);
    }

    @Test
    public void testEnvironmentConflict()
    {
        StaticAnnouncement announcement = new StaticAnnouncement("production", "storage", "alpha", "/a/b/c", ImmutableMap.of("http", "http://localhost:1111"));

        Response response = resource.post(announcement, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/static")), null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertEquals(resource.getStaticGetStats().getCount(),0);
        assertEquals(resource.getStaticDeleteStats().getCount(),0);
        assertEquals(resource.getStaticPostStats().getCount(),0);

        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void testDelete()
    {
        Service blue = new Service(Id.<Service>random(), null, "storage", "alpha", "/a/b/c", ImmutableMap.of("key", "valueBlue"));
        Service red = new Service(Id.<Service>random(), null, "storage", "alpha", "/a/b/c", ImmutableMap.of("key", "valueRed"));

        store.put(red);
        store.put(blue);

        resource.delete(blue.getId(), null);
        assertEquals(store.getAll(), ImmutableSet.of(red));
        assertEquals(resource.getStaticGetStats().getCount(),0);
        assertEquals(resource.getStaticDeleteStats().getCount(),1);
        assertEquals(resource.getStaticPostStats().getCount(),0);
    }

    @Test
    public void testMakesUpLocation()
    {
        StaticAnnouncement announcement = new StaticAnnouncement("testing", "storage", "alpha", null, ImmutableMap.of("http", "http://localhost:1111"));

        Response response = resource.post(announcement, new MockUriInfo(URI.create("http://localhost:8080/v1/announcement/")), null);

        assertNotNull(response);
        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

        assertEquals(store.getAll().size(), 1);
        Service service = store.getAll().iterator().next();
        assertEquals(service.getId(), service.getId());
        assertNotNull(service.getLocation());
    }

    @Test
    public void testGet()
    {
        Service blue = new Service(Id.<Service>random(), null, "storage", "alpha", "/a/b/c", ImmutableMap.of("key", "valueBlue"));
        Service red = new Service(Id.<Service>random(), null, "storage", "alpha", "/a/b/c", ImmutableMap.of("key", "valueRed"));

        store.put(red);
        store.put(blue);

        Services actual = resource.get();
        Services expected = new Services("testing", ImmutableSet.of(red, blue));

        assertEquals(actual, expected);
        assertEquals(resource.getStaticGetStats().getCount(),1);
        assertEquals(resource.getStaticDeleteStats().getCount(),0);
        assertEquals(resource.getStaticPostStats().getCount(),0);
    }
}
