v0.1.5
====

* Small release just to make an RxMonitor's observable a `val`

v0.1.4
====

* Fix not being able to monitor on more than 1 event type for a given path [#41](https://github.com/lloydmeta/schwatcher/issues/41)

v0.1.3
====

* Introduce [RxScala](http://rxscala.github.io/) interface for monitoring

v0.1.2
====

* Support for 2.11.0
* Clean up deprecated stuff from tests and code

v0.1.1
====

* Support Scala 2.10.4 and Akka 2.3.2

v0.1.0
====

* Thanks to [georgeOsdDev](https://github.com/georgeOsdDev), modifiers can be used when registering callbacks
* Upgrade to 2.3.0 of Akka

v0.0.7
=================

* Smaller library due to removal of external logging libs : Thanks [@crdueck](https://github.com/crdueck) for the suggestion
* Everything is now immutable in MonitorActor. Now even if you leak a reference to the internal CallbackRegistry everything is still thread safe

v0.0.6
====

* Allow for concurrency of 1 :)

v0.0.5
====

* Use a dynamically resized Akka actor router so that terminated callback actors can be automatically revived

v0.0.4
====

* No longer uses Akka Agent to hold CallbackRegistry (thanks crdueck)
* Refactored testing for better coverage and maintainability
* Scala 2.10.3 support in testing

v0.0.3
====

* Upgrade to Akka 2.2.1

v0.0.2
====

* Added crossVersion building across 2.10.x
* Add automated testing for new supported versions

v0.0.1
====

* Initial public release
