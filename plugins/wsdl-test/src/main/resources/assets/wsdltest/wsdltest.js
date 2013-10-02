
window.addEventListener("load", function() {

    var $ = function(sel, context) {
        var c = context ? context : document;
        return c.querySelector(sel);
    };

    $.elem = function(name, content) {
        var elem = document.createElement(name);
        if(content) {
            elem.textContent = content;
        }
        return elem;
    }

    var container = $("#container");

    var $$ = function(sel, context) {
        var c = context ? context : document;
        return c.querySelectorAll(sel);
    };

    function fetchWsdl(url, callback) {

        var xhr = new XMLHttpRequest();

        xhr.onreadystatechange = function() {
            if(xhr.readyState == 4) {
                callback(xhr.responseXML);
            }
        }

        xhr.open("GET", url);
        xhr.send();
    }

    function forEach(arr, callback) {
        for(var i = 0; i < arr.length; i++) {
            callback(arr[i]);
        }
    }
    function generateHTML(wsdl) {
        console.log("Generate from: " + wsdl)

        var operations = $$("portType operation", wsdl);

        forEach(operations, function(it) {
            generateOperation(it);
        })


    }

    function generateSequence(xml, seq, elementNs) {
        var elems = $$("element", seq);

        forEach(elems, function(it) {
            generateElement(xml, it, elementNs);
        });
    }

    function generateElement(xml, elem, elementNs) {
        var name = elem.getAttribute("name");
        var e = xml.createElementNS(elementNs, name);
        e.textContent = "?";

        console.log("Elem owner: " + (e.ownerDocument === xml))
        xml.documentElement.appendChild(e);
    }
    function generateComplexType(xml, type, elementNs) {
        var seq = $("sequence", type);
        generateSequence(xml, seq, elementNs);
    }

    function generateXmlForElementType(typeNameNs, doc) {


        var element = $("element[type='" + typeNameNs + "']", doc);


        var typeName = typeNameNs.substring(typeNameNs.indexOf(":")+1);


        var typeElem = $("complexType[name='" +typeName +"']", doc);

        var elementName = element.getAttribute("name");

        var elementNs = $("schema", doc).getAttribute("xmlns:" +typeNameNs.substr(0, typeNameNs.indexOf(":")));
        var xml = doc.implementation.createDocument(elementNs, elementName);

        generateComplexType(xml, typeElem, elementNs)

        var xmlText = new XMLSerializer().serializeToString(xml)
        console.log("XML is: " + xmlText);
        var xmlText = indent(xmlText);
        console.log("XMLIndent is: " + xmlText);

        container.appendChild($.elem("pre", xmlText))

    }

    function generateRequestResponse(op) {
        var input = $("input", op);
        var output = $("output", op);

        var inputMessage = $("message[name='" + input.getAttribute("name")+"']", op.ownerDocument.documentElement);

        forEach($$("part", inputMessage), function(it) {
            generateXmlForElementType(it.getAttribute("element"), inputMessage.ownerDocument);
        });


    }

    function generateOperation(op) {
        var greet = $.elem("div");
        greet.appendChild($.elem("h1", op.getAttribute("name")))

        container.appendChild(greet);

        generateRequestResponse(op);
    }

    function generate() {

        fetchWsdl($("#wsdlUrl").value, generateHTML);
    }

    function indent(responseText) {
        var level = 0;
        var xml = "";
        return responseText.replace(/(<[^\/][^>]+>)|(<\/[^>]+>)/g, function(match, start, end, offset, string) {

            console.log("Match: " + match + " at level " + level + ", start: " + start +", end: " + end);


            var ret = "";
            if(start) {
                ret += "\n";
                for(var i = 0; i < level;i++) {
                    ret +="  ";
                }
                ret += start;
            }
            if(end) {
                if(offset+match.length == string.length) {
                    ret +="\n";
                }
                ret +=end;

            }
            console.log("Replacing " + match + " with " + ret)
            level += start ? 1 : -1;
            return ret;
        });
    }

    $("#generate").addEventListener("click", generate);

    generate();
});
