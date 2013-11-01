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

    @Export
    public WebApplication getWicketApplication() {
        return wicketApplication;
    }
}
