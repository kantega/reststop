
window.addEventListener("load", function() {
   console.log("Found " + compilationExceptions.length +" exceptions")

    var container = document.querySelector("div.content");
    for(var i = 0; i < compilationExceptions.length; i++) {
        var ex = compilationExceptions[i];
        var h1 = document.createElement("h1");
        h1.innerHTML = "Compilation failed:";
        container.appendChild(h1);

        for(var d = 0; d < ex.length; d++) {
            var diag = ex[d];
            var h2 = document.createElement("h2");
            h2.innerHTML = diag.kind +": " + diag.source;
            container.appendChild(h2);

            var lineNumber = document.createElement("div");
            lineNumber.innerHTML = "Line " + diag.lineNumber +":";
            container.appendChild(lineNumber);
            var message = document.createElement("pre");
            message.textContent= diag.message;

            container.appendChild(message);

            var lines = document.createElement("pre");
            lines.setAttribute("class", "sourceCode");

            for(var l = 0; l < diag.sourceLines.length;l++) {
                var line = document.createElement("span");
                var lid = "exline" + i + d + l;
                line.setAttribute("id", lid);
                if(l+1 == diag.lineNumber) {
                    line.setAttribute("class", "error")
                }
                line.innerHTML =diag.sourceLines[l] +"\n";
                lines.appendChild(line);
            }

            container.appendChild(lines);

            lines.scrollTop = document.getElementById("exline" + i + d + (diag.lineNumber-1)).offsetTop;

        }
    }

    console.log("Found " + testFailureExceptions.length +" test failures")

    for(var i = 0; i < testFailureExceptions.length; i++) {
        var ex = testFailureExceptions[i];

        console.log("Has " + ex.length)
        for(var f = 0; f < ex.length; f++) {
            var fail = ex[f];

            var h2 = document.createElement("h2");
            h2.innerHTML = "Test failed: " + fail.description;

            container.appendChild(h2);

            var message = document.createElement("pre");
            console.log("Message: " + fail.message);
            message.textContent = fail.message;
            container.appendChild(message);

            if(fail.sourceFile) {
                var source = document.createElement("div");
                source.innerHTML = fail.sourceFile +". Line " + fail.sourceLine +":";
                container.appendChild(source);
            }

            var lines = document.createElement("pre");
            lines.setAttribute("class", "sourceCode");

            for(var l = 0; l < fail.sourceLines.length;l++) {
                var line = document.createElement("span");
                var fid = "failline" + i + f + l;
                line.setAttribute("id", fid);
                if(l+1 == fail.sourceLine) {
                    line.setAttribute("class", "error")
                }
                line.innerHTML =fail.sourceLines[l] +"\n";
                lines.appendChild(line);
            }

            container.appendChild(lines);

            lines.scrollTop = document.getElementById("failline" + i + f + (fail.sourceLine-1)).offsetTop;


            if(fail.stackTrace) {
                var p = document.createElement("p");
                p.textContent = "Stack trace:";
                container.appendChild(p);
                var stackTrace = document.createElement("pre");

                stackTrace.setAttribute("class", "stackTrace");
                stackTrace.textContent = fail.stackTrace;

                container.appendChild(stackTrace);
            }

        }
    }

});