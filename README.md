Reststop - friction-free web development
==============================================

## About

Reststop is a system for plugin based development on the Java Servlet API. The core of Reststop is a servlet filter
that loads and reload plugins in separate class loaders. Using Reststop, you will develop your application as plugins,
making it highly modular.

A Reststop plugin is simply a Maven module, in which your provide a collection of Servlet Filters, and methods to
start and stop (init and destroy) your plugin. You also have a simple dependency injection
system for exporting services from one plugin, and injecting them by type in another.

Reststop already provide a set of very useful plugins. We recommend you start
looking at the following:

 * _reststop-development-plugin_ This plugin is monitoring your files,
 and automatically compiles and reloads java source code on changes.
 * _reststop-development-console_ This plugin gives you handy information about
 your application, exposed on the path _/dev_.
 * _jaxrs-api_ This plugin makes it a breeze to wire up Jax-RS Resources.

Reststop requires you to write your application as plugins, but what you do
inside a plugin is entirely up to you. Inside a plugin you can introduce
whatever framework you prefer.

## Getting Started

The easiest way to understand Reststop, is to see it in action. Either take the [intro tutorial](https://github.com/kantega/reststop/wiki/Intro%20Tutorial) or just follow 
the simple instructions below:

In this section we will be using a Maven plugin to create a small project, and make some small changes to understand
how things work. Let's start:

Create Reststop project:

        mvn -U org.kantega.reststop:reststop-maven-plugin:RELEASE:create

Open a browser to [http://localhost:8080/helloworld/](http://localhost:8080/helloworld/)

This will show a page with the simple message:

        {"message":"Hello world"}

Now add som politeness to the return statement in plugins/helloworld/src/main/java/YOUR.PACKAGE.NAME/HelloworldResource.java:

        return new Hello(greeting + " dear world");

Then hit the reload button, and while you did that Reststop notified the change, recompiled and hot-deployed it:

        {"message":"Hello dear world"}

### Next Level

Try to change the word "world" into something different, and reload again:

        return new Hello(greeting + " dear friend");

Reststop just discovered that a Test failed, showing test code and stack trace. Now update the HelloworldResourceTest.java to match and reload again.

Next try to remove the following line in HelloworldResource.java:

        return new Hello(greeting + " dear friend");

Reststop now shows you an compilation error, with location and error message from the compiler. Wasn't that friction-free like a dynamic language?


### Under the hood

To get a little more insight into Reststop, point your browser to [http://localhost:8080/dev](http://localhost:8080/dev)

This Development Console shows you all the plugins, classes and source dirs that Reststop is currently tracking for you.


## Creating a new plugin

Go to root of your Reststop project and run:

        mvn reststop:create-plugin -Dname=myplugin -Dpackage=org.company.helloworld.myplugin

This automatically creates a new plugin, with maven module and Reststops configuration.


## Automatic recompile and hot-deploy

While writing code it is very convenient to have automatic recompile and hot-deploy. To experience this while developing, do the following:

        mvn clean install
        cd webapp
        mvn jetty:run.

### Add debugging

For a fully debugging enabled environment do the following:

        mvn clean install
        cd webapp
        mvnDebug jetty:run.

Then attach your debugger. Using this combination of automatic recompile, hot-deploy and debugging enables a
friction-free web development with the feedback loop of a dynamic language.

## Maven Goals

    reststop:create                 Creates Reststop project
    reststop:create-plugin          Creates Reststop plugin
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
