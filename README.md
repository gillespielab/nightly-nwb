# kovasap/nightly-nwb

## Notes

How the yaml should be generated:

https://docs.google.com/document/d/1-4OthZDf6QfC_1RTGJRJoN75CL_Ssa7ja-oeSSngIrI/edit?tab=t.0

## Usage

Run the project:

    $ clojure -M:run-m

Run the project's tests:

    $ bin/kaocha

Run the project's CI pipeline and build an uberjar (this will fail until you
edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies
inside the `META-INF` directory inside `target/classes` and the uberjar in
`target`.
You can update the version (and SCM tag) information in generated `pom.xml` by
updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it.
The `ci` task will still generate a minimal `pom.xml` as part of the `uber`
task, unless you remove `version` from `build.clj`.

Run that uberjar:

    $ java -jar target/net.clojars.kovasap/nightly-nwb-0.1.0-SNAPSHOT.jar
