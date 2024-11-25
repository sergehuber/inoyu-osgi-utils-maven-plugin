package com.example.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;
import java.util.HashSet;
import java.util.Set;

public class RestActivator implements BundleActivator {

    private ServiceRegistration<Application> serviceRegistration;

    @Override
    public void start(BundleContext context) {
        System.out.println("Starting REST service...");

        // Create and register the REST application
        Application restApplication = new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                Set<Class<?>> classes = new HashSet<>();
                // Add your REST resources and providers here
                classes.add(ExampleResource.class);
                return classes;
            }
        };

        serviceRegistration = context.registerService(Application.class, restApplication, null);
        System.out.println("REST service registered.");
    }

    @Override
    public void stop(BundleContext context) {
        System.out.println("Stopping REST service...");

        // Unregister the REST application
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            System.out.println("REST service unregistered.");
        }
    }

    // Example REST resource
    @Provider
    public static class ExampleResource {

        @javax.ws.rs.GET
        @javax.ws.rs.Path("/example")
        @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.TEXT_PLAIN)
        public String exampleEndpoint() {
            return "Hello from REST!";
        }
    }
}
