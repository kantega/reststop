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

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Ignore
public class HelloJSonIT {



    @Test

    public void testGetSeries() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + System.getProperty("reststopPort") + "/mySeries").openConnection();
        connection.setRequestProperty("Authorization", "Basic " + DatatypeConverter.printBase64Binary("joe:joe".getBytes("utf-8")));
        connection.setRequestProperty("Accept", "application/json");
        String message = IOUtils.toString(connection.getInputStream());


        Assert.assertTrue(message.indexOf("\"key\":\"demo-series\"") > 0);
        Assert.assertTrue(message.indexOf("\"id\":\"e081b2455d5a4edcb528c3cdb90b2161\"") >0 );

    }

    @Test
    public void testGetSeriesById() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + System.getProperty("reststopPort") + "/mySeries/e081b2455d5a4edcb528c3cdb90b2161").openConnection();
        connection.setRequestProperty("Authorization", "Basic " + DatatypeConverter.printBase64Binary("joe:joe".getBytes("utf-8")));
        connection.setRequestProperty("Accept", "application/json");
        String message = IOUtils.toString(connection.getInputStream());

        Assert.assertTrue(message.indexOf("\"key\":\"demo-series\"") > 0);
        Assert.assertTrue(message.indexOf("\"id\":\"e081b2455d5a4edcb528c3cdb90b2161\"") >0 );


    }

    @Test
    public void testGetSeriesById404() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + System.getProperty("reststopPort") + "/mySeries/e081b2455d5a4edcb528c3cdb90b2162").openConnection();
        connection.setRequestProperty("Authorization", "Basic " + DatatypeConverter.printBase64Binary("joe:joe".getBytes("utf-8")));
        connection.setRequestProperty("Accept", "application/json");
        assertThat(connection.getResponseCode(),is(404));
    }


}
