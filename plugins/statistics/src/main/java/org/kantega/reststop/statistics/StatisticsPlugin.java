package org.kantega.reststop.statistics;

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.Reststop;

/**

 */
public class StatisticsPlugin  extends DefaultReststopPlugin {

    public StatisticsPlugin(Reststop reststop) {

        addServletFilter(reststop.createFilter(new StatisticsFilter(),"/*", FilterPhase.USER));
    }
}
