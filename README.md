Reststop - friction-free web development
==============================================

## About

Reststop is a thin layer built on top of the Java Servlet API. Reststop’s module system lets you code independent
features in individual modules. During development, Reststop will track your code base, recompile and hot-deploy the
modules which changed.

The result is friction-free web development with robustness of the Java language and tool stack, but with the feedback
loop of a dynamic language.


## Getting Started

Create project structure:

        mvn org.kantega.reststop:reststop-maven-plugin:create

Open a browser with the url:

        http://localhost:8080/helloworld/

Showing a page with the simple message:

        {"message":"Hello world"}

Now edit the return statement in plugins/helloworld/src/main/java/<YOUR PACKAGE NAME>/HelloworldResource.java to:

        return new Hello("New world");

Then just hit the reload button in the browser, and while you did that Reststop notified the change, recompiled and hot-deployed it showing:

        {"message":"New world"}

### Next Level

Try to change the word "world" into something different like, and the reload again:

        return new Hello("New order");

Reststop just discovered that a Test failed, showing test code and stack trace. Now update the HelloworldResourceTest and reload again.

Now try to remove the following line in HelloworldResource.java and reload:

        return new Hello("New world");

Reststop now shows where you have compilation error, with location and error message from compiler. Wasn't that friction-free like a dynamic language?


### Under the hood

To get a little more insight into Reststop, change the browser to:

        http://localhost:8080/dev

This Development Console shows you all the plugins, classes and source dirs that Reststop is currently tracking for you.


## Maven Goals

    reststop:create                 Creates simple project
    reststop:run                    Run application and open project and browser
    reststop:start                  Starts application
    reststop:stop                   Stops application
    reststop:package-plugins        Packages plugins and plugin configuration
    reststop:resolve-plugins        Resolves plugin configuration
    reststop:scan-plugins           Scan for plugins and create descriptor, export and import info
    reststop:dist                   Build Zip based distribution packages
    reststop:dist-rpm               Build RPM based distribution packages


## Plugin Consoles

    reststop-development-console    /dev
    reststop-metrics-plugin         /healthchecks/
    reststop-metrics-plugin         /assets/metrics
