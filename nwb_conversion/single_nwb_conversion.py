"""Raw-to-NWB conversion script for a single dataset."""

import argparse
import os
import re

from trodes_to_nwb.convert import create_nwbs

def main():
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--data_directory',
      type=str,
      help='Path to the data directory that contains data and yaml to process.'
           'This directory should have many date directories in it.')
  parser.add_argument('--date', type=str, help='Single date to process.')
  parser.add_argument('--output_dir', type=str, help='Directory to put nwb files.')
  parser.add_argument('--dry_run', action='store_true',
                      help='Just prints what this script would do. '
                           'Useful for testing.')
  args = parser.parse_args()

  if args.dry_run:
    print(f'Will process data for {args.date} in {args.data_directory} and '
          f'put results in {args.output_dir}.')
    return

  create_nwbs(path=args.data_directory,
              output_dir=args.output_dir,
              query_expression=f'date == {args.date}')

if __name__ == "__main__":
  main()
