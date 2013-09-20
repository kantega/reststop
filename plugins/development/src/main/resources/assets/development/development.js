
window.addEventListener("load", function() {
   console.log("Found " + compilationExceptions.length +" exceptions")

    for(var i = 0; i < compilationExceptions.length; i++) {
        var ex = compilationExceptions[i];
        var h1 = document.createElement("h1");
        h1.innerHTML = "Compilation exception:";
        document.body.appendChild(h1);

        for(var d = 0; d < ex.length; d++) {
            var diag = ex[d];
            var h2 = document.createElement("h2");
            h2.innerHTML = diag.kind +": " + diag.source;
            document.body.appendChild(h2);

            var lineNumber = document.createElement("div");
            lineNumber.innerHTML = "Line " + diag.lineNumber +":";
            document.body.appendChild(lineNumber);
            var message = document.createElement("pre");
            message.innerHTML = diag.message;

            document.body.appendChild(message);

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

            document.body.appendChild(lines);

            document.getElementById("exline" + i + d + (diag.lineNumber-1)).scrollIntoView();

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

            document.body.appendChild(h2);

            var message = document.createElement("pre");
            console.log("Message: " + fail.message);
            message.innerHTML = fail.message;
            document.body.appendChild(message);

            if(fail.sourceFile) {
                var source = document.createElement("div");
                source.innerHTML = fail.sourceFile +". Line " + fail.sourceLine +":";
                document.body.appendChild(source);
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

            document.body.appendChild(lines);

            document.getElementById("failline" + i + f + (fail.sourceLine-1)).scrollIntoView();

        }
    }

});