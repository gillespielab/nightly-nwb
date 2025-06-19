# kovasap/nightly-nwb

## Notes

How the yaml should be generated:

https://docs.google.com/document/d/1-4OthZDf6QfC_1RTGJRJoN75CL_Ssa7ja-oeSSngIrI/edit?tab=t.0

## Dependencies

This tool uses [`gdrive`](https://github.com/glotlabs/gdrive) to download Google
Drive data.  Download and install it first before using this tool.

## Usage

A built version of this tool exists at
target/net.clojars.kovasap/nightly-nwb-0.1.0-SNAPSHOT.jar in this repository.
I also added a copy to the "releases" section of this github page.
You can download and run it to see the help output like:

    $ java -jar nightly-nwb-0.1.0-SNAPSHOT.jar

A real world example of how to run the tool looks like:

    $ java -jar nightly-nwb-0.1.0-SNAPSHOT.jar generate-yaml -f testdata/raw/ -s teddy -e gabby -d 20250602

## Other Ways to Run

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

    $ java -jar target/net.clojars.kovasap/nightly-nwb-0.1.0-SNAPSHOT.jar generate-yaml -f testdata/raw/ -s teddy -e gabby -d 20250602
