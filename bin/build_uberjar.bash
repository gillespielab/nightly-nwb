#!/usr/bin/env bash

source ~/.virtualenvs/nightly-nwb/bin/activate
clojure -T:build ci
