/*
 * Copyright 2018 Kantega AS
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

package org.kantega.reststop.helloworld;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.kantega.reststop.helloworld.Utils.readPort;

/**
 *
 */
public class HelloworldIT {

    @Test
    public void shouldReturnHelloWorld() throws IOException, URISyntaxException {


        String reststopPort = readPort();
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + reststopPort + "/helloworld/en?yo=hello").openConnection();
        connection.setRequestProperty("Authorization", "Basic " + DatatypeConverter.printBase64Binary("joe:joe".getBytes("utf-8")));
        connection.setRequestProperty("Accept", "application/json");
        String message = IOUtils.toString(connection.getInputStream());

        assertThat(message, is("{\"message\":\"Hello world\"}"));
    }



}
