package org.acme.employeescheduling.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import org.acme.employeescheduling.rest.DemoDataGenerator.DemoData;

@Path("demo-data")
public class EmployeeScheduleDemoResource {

    private final DemoDataGenerator dataGenerator;

    @Inject
    public EmployeeScheduleDemoResource(DemoDataGenerator dataGenerator) {
        this.dataGenerator = dataGenerator;
    }

    @GET
    public DemoData[] list() {
        return DemoData.values();
    }

    @GET
    @Path("/{demoDataId}")
    public Response generate(@PathParam("demoDataId") DemoData demoData) {
        return Response.ok(dataGenerator.generateDemoData(demoData)).build();
    }
}
