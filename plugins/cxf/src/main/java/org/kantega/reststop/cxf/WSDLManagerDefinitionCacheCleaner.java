package org.kantega.reststop.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.wsdl.WSDLManager;

/**
 *
 */
public class WSDLManagerDefinitionCacheCleaner {

    public static WSDLManager wsdlManager;

    public WSDLManagerDefinitionCacheCleaner(Bus bus) {


        wsdlManager = bus.getExtension(WSDLManager.class);
    }

    public static WSDLManager getWsdlManager() {
        return wsdlManager;
    }

}
