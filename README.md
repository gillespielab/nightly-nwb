# gillespielab/nightly-nwb

## Dependencies

This tool uses [`gdrive`](https://github.com/glotlabs/gdrive) to download Google
Drive data.  Download and install it first before using this tool.

You will also need to install
https://github.com/LorenFrankLab/trodes_to_nwb?tab=readme-ov-file#installation
so that you are able to run `python batch_nwb_conversion.py` successfully.
NOTE that usually the "proper" way to install this library is through some
virtual environment or package manager like conda.  An example of how to do this:

```
sudo apt install python3-virtualenvwrapper
mkvirtualenv nightly-nwb
pip install trodes_to_nwb
```

This will put you in the `nightly-nwb` virtual environment in your terminal, which will give you visibility to a version of python that can use `trodes_to_nwb`.  Test that this works via:

```
python3 batch_nwb_conversion.py
```

You should get:

```
usage: batch_nwb_conversion.py [-h] [--dates DATES [DATES ...]] [--excluded EXCLUDED] name
batch_nwb_conversion.py: error: the following arguments are required: name
```

In the future, whenever you want to run nightly-nwb, make sure you are in this
virtual environment by running:

```
workon nightly-nwb
```

See documentation at https://virtualenvwrapper.readthedocs.io/en/latest/.

Feel free to use another virtual environment management system if you prefer.

## Usage

A built version of this tool exists at
target/net.clojars.gillespielab/nightly-nwb-0.1.0-SNAPSHOT.jar in this repository.
You can run it to see the help output like:

    $ java -jar target/net.clojars.gillespielab/nightly-nwb-0.1.0-SNAPSHOT.jar

A real world example of how to run the tool looks like:

    $ java -jar nightly-nwb-0.1.0-SNAPSHOT.jar generate-yaml-then-nwb --yaml-only -d testdata/ -s teddy -e gabby -d 20250602

## Running Automatically

To run this tool automatically, you can use the built in linux utility `cron`.
To do this:

1. Run `crontab -e` to edit your user's crontab file
   - Note that linux will open this file with your default editor, which could
     be `nano` or `vim`.
     Learning how to edit, save, and exit from these editors can be tricky - try
     googling for their names and what you want to do in them.
     The editor that will be opened is defined by the `VISUAL` or `EDITOR`
     environment variables; you can change these variables on the command line
     like `export VISUAL=vim` if desired before running `crontab -e`.
1. In this file, add an additional line scheduling your nightly nwb runs like this:

```
# Run every day at 8:05pm, see https://crontab.guru/#5_20_*_*_* for explanation.
5 20 * * * source /path/to/your/virtualenv/bin/activate && java -jar /path/to/your/nightly-nwb.jar detect-and-process-new-data -d /path/to/data/ -s teddy -e gabby &> /path/to/output/log
```

You can find the path to your virtualenv by running `which python` while inside
your virtualenv.
Then just replace `python` with `activate` to make the `source` command.

1. To test that this works, try running the command `source ...` on the command
   line alone before piping it into cron.


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

    $ java -jar target/net.clojars.gillespielab/nightly-nwb-0.1.0-SNAPSHOT.jar generate-yaml-then-nwb -d testdata/ -s teddy -e gabby -d 20250602

## Notes

How the yaml should be generated:

https://docs.google.com/document/d/1-4OthZDf6QfC_1RTGJRJoN75CL_Ssa7ja-oeSSngIrI/edit?tab=t.0
