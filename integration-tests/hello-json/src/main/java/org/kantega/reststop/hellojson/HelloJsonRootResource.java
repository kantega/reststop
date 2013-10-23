package org.kantega.reststop.hellojson;

/*
 * Copyright 2013 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.glassfish.jersey.client.filter.HttpBasicAuthFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.kantega.reststop.hellojson.mapping.MySeriesBean;
import org.kantega.reststop.hellojson.mapping.SeriesBean;

import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Path("/mySeries")
@Produces({"application/json"})
public class HelloJsonRootResource {

    private ObjectMapper mapper;

    @GET
    public List<MySeriesBean> getSeries() {

        Response resp = getRootResource("https://api.tempo-db.com/v1")
                .path("series")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        String respBody = resp.readEntity(String.class);
        System.out.println(respBody);
        try {
            List<SeriesBean> original= getMapper().readValue(respBody, new TypeReference<ArrayList<SeriesBean>>() {
            });
            List<MySeriesBean> transformed = new ArrayList<MySeriesBean>();
            for( SeriesBean orig : original)
                transformed.add(new MySeriesBean(orig));

            return transformed;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    @GET
    @Path("{id}")
    public MySeriesBean getSeriesById(@PathParam(value = "id") String id){
        Response resp = getRootResource("https://api.tempo-db.com/v1")
                .path("series/id/"+id)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        if( resp.getStatus() == 200 )
        {
            String respBody = resp.readEntity(String.class);

            try {
                MySeriesBean b = new MySeriesBean(getMapper().readValue(respBody, SeriesBean.class));
                System.out.println(": "+getMapper().writeValueAsString(b));
                return b;
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse JSON", e);
            }
        }
        else {
            int status = resp.getStatus();
            if (status == 403)
                status = 404;
            throw new WebApplicationException(status);
        }
    }

    private WebTarget getRootResource(String s) {

        Client client = ClientBuilder.newBuilder()
                //.register()
                .register(new HttpBasicAuthFilter("759172ba3c084326ab2f9483f1a609a0", "f79dc6abebeb4be88c0c24c4f789c8d7"))
                .register(JacksonFeature.class)
                .build();
        return client.target(s);
    }

    private synchronized ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper;
    }
}
