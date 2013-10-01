window.addEventListener("load", function() {


    function fetchMessage(uri) {
        var xhr = new XMLHttpRequest();

        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4) {
                var message = xhr.responseXML.querySelector("message")
                var li = document.createElement("li");
                li.textContent = message.textContent;
                document.querySelector("#hellos").appendChild(li);
            }
        }

        xhr.open("GET", uri, false);

        xhr.send()
    }

    function findLanguages() {
        var xhr = new XMLHttpRequest();

        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4) {
                console.log("Type: " + xhr.responseType)
                var langs = xhr.responseXML.querySelectorAll("languages *")
                for (var l = 0; l < langs.length; l++) {
                    console.log(l +" : " + langs[l]);
                    fetchMessage(langs[l].textContent)
                }

            }
        }

        xhr.open("GET", "helloworld");

        xhr.send()
    }

    function indent(responseText) {
        var level = 0;
        return responseText.replace(/><(\/?)/g, function(match) {
            level += match.indexOf("/") == -1 ? 1 : -1;
            console.log("Match: " + match + " at level " + level);
            var ret = ">\n";
            for(var i = 0; i < level;i++) {
                ret +="  ";
            }
            ret +="<";
            if(match.indexOf("/") != -1) {
                ret +="/";
            }
            return ret;
        })
        console.log("Out is: " +out)
        return out;
    }

    function sendSoap(evt, content) {
        evt.preventDefault();

        var xhr = new XMLHttpRequest();

        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4) {
                console.log("SOAP service said: " + xhr.status)
                var sr = document.querySelector("#soapResponse");
                var pre = document.createElement("pre");
                pre.setAttribute("class", "brush: xml;");
                var textContent = indent(xhr.responseText);
                pre.textContent = textContent;
                console.log("Response is: " + textContent)
                while(sr.firstChild) {
                    sr.removeChild(sr.firstChild)
                }
                sr.appendChild(pre);

                document.querySelector(".soapExchange").style.display="block";
                SyntaxHighlighter.highlight()


                sr.setAttribute("class",xhr.status === 200 ? "ok" : "error");

            }
        }

        xhr.open("POST", "ws/hello-1.0");
        xhr.setRequestHeader("Content-Type", "application/xml; charset=utf-8");




        var sr = document.querySelector("#soapRequest");
        sr.setAttribute("data-xml", content);
        var pre = document.createElement("pre");
        pre.setAttribute("class", "brush: xml;");
        pre.textContent = content;

        while(sr.firstChild) {
            sr.removeChild(sr.firstChild)
        }
        sr.appendChild(pre);

        xhr.send(content);
    }

    function requestClicked() {
        var sr = document.querySelector("#soapRequest");
        if(sr.getAttribute("is-edit")) {
            return;
        }
        var pre = document.createElement("pre");
        pre.textContent = sr.getAttribute("data-xml");

        while(sr.firstChild) {
            sr.removeChild(sr.firstChild)
        }
        sr.appendChild(pre);

        pre.addEventListener("blur", function() {
            console.log("Updating sr-xml to " + pre.textContent)
            sr.setAttribute("data-xml", pre.textContent);
            sr.removeAttribute("is-edit");

            var newpre = document.createElement("pre");
            newpre.setAttribute("class", "brush: xml;");
            newpre.textContent = pre.textContent;

            while(sr.firstChild) {
                sr.removeChild(sr.firstChild)
            }
            sr.appendChild(newpre);
            SyntaxHighlighter.highlight()

        });
        pre.setAttribute("contenteditable", true);
        sr.setAttribute("is-edit", true);
    }

    document.querySelector("#soapRequest").addEventListener("click", requestClicked)
    function sendSoapTemplate(evt) {
        var template = document.querySelector("#soapMessageTemplate").textContent;

        template = template.replace("NAME", document.querySelector("input[name='receiver']").value);
        template = template.replace("LANG", document.querySelector("input[name='lang']").value);

        console.log("Template: " +template);

        sendSoap(evt, template)

    }

    document.querySelector("#send").addEventListener("click", sendSoapTemplate)
    function callSoap(evt) {
        var sr = document.querySelector("#soapRequest").getAttribute("data-xml");
        console.log("XML is: " + sr)
        sendSoap(evt, sr);

    }

    document.querySelector("#callXml").addEventListener("click", callSoap)
    findLanguages();




});