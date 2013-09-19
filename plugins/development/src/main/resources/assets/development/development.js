
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
                line.setAttribute("id", "line" + l);
                if(l+1 == diag.lineNumber) {
                    line.setAttribute("class", "error")
                }
                line.innerHTML =diag.sourceLines[l] +"\n";
                lines.appendChild(line);
            }

            document.body.appendChild(lines);

            document.getElementById("line" + (diag.lineNumber-1)).scrollIntoView();

        }




    }
});