package org.kantega.reststop.wicket;

import org.apache.wicket.protocol.http.WicketFilter;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.Reststop;
import org.kantega.reststop.api.ReststopPluginManager;

import javax.servlet.ServletException;
import java.util.Properties;

/**
 *
 */
public class WicketPlugin extends DefaultReststopPlugin {


    public WicketPlugin(Reststop reststop, final ReststopPluginManager pluginManager) throws ServletException {

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            WicketFilter filter = new WicketFilter(new WicketApplication());
            Properties properties = new Properties();
            String filterPath = "/wicket/*";
            properties.setProperty(WicketFilter.FILTER_MAPPING_PARAM, filterPath);
            filter.init(reststop.createFilterConfig("wicket", properties));
            addServletFilter(reststop.createFilter(filter, filterPath, FilterPhase.USER));
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }
}
