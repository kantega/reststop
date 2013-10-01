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

    findLanguages();


});