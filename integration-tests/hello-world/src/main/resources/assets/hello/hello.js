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

    function sendSoap(evt) {
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
                SyntaxHighlighter.highlight()
            }
        }

        xhr.open("POST", "ws/hello-1.0");

        var template = document.querySelector("#soapMessageTemplate").innerHTML;

        template = template.replace("NAME", document.querySelector("input[name='receiver']").value);
        template = template.replace("LANG", document.querySelector("input[name='lang']").value);

        console.log("Template: " +template);


        var sr = document.querySelector("#soapRequest");
        var pre = document.createElement("pre");
        pre.setAttribute("class", "brush: xml;");
        pre.textContent = template;
        while(sr.firstChild) {
            sr.removeChild(sr.firstChild)
        }
        sr.appendChild(pre);
        xhr.send(template);
    }

    document.querySelector("#send").addEventListener("click", sendSoap)
    findLanguages();




});