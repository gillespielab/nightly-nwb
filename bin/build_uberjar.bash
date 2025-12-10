#!/usr/bin/env bash

source mamba activate nightly-nwb
clojure -T:build ci
