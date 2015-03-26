/*
 * Copyright 2015 Kantega AS
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

package org.kantega.reststop.wicket;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WicketFilter;
import org.kantega.reststop.api.*;

import javax.servlet.ServletException;
import java.util.Properties;

/**
 *
 */
public class WicketPlugin extends DefaultReststopPlugin {


    @Export
    private final WebApplication wicketApplication;

    public WicketPlugin(Reststop reststop) throws ServletException {

            wicketApplication = new WicketApplication();

            WicketFilter filter = new WicketFilter(wicketApplication);

            Properties properties = new Properties();
            String filterPath = "/wicket/*";
            properties.setProperty(WicketFilter.FILTER_MAPPING_PARAM, filterPath);

            filter.init(reststop.createFilterConfig("wicket", properties));

            addServletFilter(reststop.createFilter(filter, filterPath, FilterPhase.USER));

    }

}
