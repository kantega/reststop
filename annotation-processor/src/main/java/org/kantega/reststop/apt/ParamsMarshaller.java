package org.kantega.reststop.apt;

import org.kantega.reststop.classloaderutils.config.PluginConfigParam;
import org.kantega.reststop.classloaderutils.config.PluginConfigParams;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;

/**
 *
 */
public class ParamsMarshaller {

    public void marshall(PluginConfigParams params, OutputStream outputStream) {

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element paramsElement = doc.createElement("params");
            doc.appendChild(paramsElement);

            for (PluginConfigParam param : params.getParams()) {
                Element paramElem = doc.createElement("param");

                paramElem.setAttribute("name", param.getParamName());
                paramElem.setAttribute("type", param.getType());
                paramElem.setAttribute("default-value", param.getDefaultValue());
                paramElem.setAttribute("required", Boolean.toString(param.isRequired()));
                Element docElem = doc.createElement("doc");
                docElem.setTextContent(param.getDoc());;
                paramElem.appendChild(docElem);

                paramsElement.appendChild(paramElem);
            }

            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(outputStream));

        } catch (ParserConfigurationException | TransformerException e) {
            throw new RuntimeException(e);
        }

    }
}
