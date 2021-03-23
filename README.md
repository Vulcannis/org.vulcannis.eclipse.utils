# Overview

A collection of various Eclipse utilities I wrote for myself, including:

* New Initializing Constructor Quick Assist/Content Completion
* Initialize Final Field in Constructors Quick Fix
* Extract Property POM Editor Content Completion

# Building

This project is built using Eclipse Tycho (https://www.eclipse.org/tycho/) and requires at least maven 3.0 (http://maven.apache.org/download.html) to be built via CLI. 
Simply run :

    mvn install

The first run will take quite a while since maven will download all the required dependencies in order to build everything.

# Installing

Once built, install the `org.vulcannis.eclipse.utils Feature` from the `org.vulcannis.eclipse.utils.site/target` directory using Eclipse's _Help->Install New Software_ menu item.
