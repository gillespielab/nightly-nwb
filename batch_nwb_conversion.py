"""
Raw-to-NWB batch conversion script

Usage Options:

    to convert all files for a given animal (NAME): `python batch_conversion.py "NAME"`

    restrict to include specific dates: `python batch_conversion.py NAME --dates d_1 ... d_n`

    restrict to exclude specific dates: `python batch_conversion.py NAME --excluded d_1 ... d_n`

"""

import argparse
import os
import re

from trodes_to_nwb.convert import create_nwbs

OUTPUT_PATH = "/home/gl-willow/banyan/nwb/raw/"
LOGS_PATH = "/home/gl-willow/banyan/nwb/nwbinspector_reports/"


# moves all nwbinspector reports out of the raw directory
def move_inspector_reports(input_dir=OUTPUT_PATH, output_dir=LOGS_PATH):
  pattern = re.compile(r'(\w+)(\d{8})_nwbinspector_report\.txt')
  for f in os.listdir(input_dir):
    if (pattern.match(f)):
      os.rename(input_dir + f, output_dir + f)
      print(f"Moved {input_dir + f} to {output_dir + f}")


def make_nwbs(data_path, animal, dates=None, excluded_dates=[], dry_run=True):
  '''
    animal (str): name of animal to generate NWBs for
    date (list(int)): optional; date or list of dates to generate NWBs for. if unspecified, NWBs for all dates will be created
    excluded_date (list(int)): optional; dates to exclude when generating NWBs
    dry_run: when True, prints the name and dates that NWBs would be generated for without actually generating them.

    '''
  path = data_path + animal
  date_list = []
  failures = []
  successes = []
  query_expression = f"animal == '{animal}'"
  if excluded_dates is None:
    excluded_dates = []
  if type(dates) == list and len(dates) > 0:
    date_list = dates
  else:  # gets all dates in the data directory if no dates specified
    date_pattern = re.compile(r'\b\d{4}\d{2}\d{2}\b')
    for _, dirs, _ in os.walk(path):
      for directory in dirs:
        if date_pattern.match(directory) and directory not in excluded_dates:
          date_list.append(int(directory))  # directories are dates

  for d in date_list:  # create nwb for each date
    if d in excluded_dates or os.path.isfile(f"{OUTPUT_PATH}{animal}{d}.nwb"):
      print(f"Skipping date {d}")
      continue  # skip already-made NWBs or explicitly excluded dates

    query_expression = f"animal == '{animal}' and date == {d}"
    print(f"Making NWB for {query_expression}")
    try:
      if not dry_run:
        create_nwbs(
            path=path,
            probe_metadata_paths=None,
            output_dir=
            OUTPUT_PATH,  # saving to a temporary directory to not clutter up the database.  Please delete when done.
            query_expression=query_expression)
      else:
        print(query_expression)
    except Exception as e:
      print(e)
      failures.append(f"{animal}, {d}: {str(e)}")
    else:
      successes.append(f"{animal}, {d}")
      inspector_file = animal + str(d) + "_nwbinspector_report.txt"
      try:
        os.rename(OUTPUT_PATH + inspector_file, LOGS_PATH + inspector_file)
      except FileNotFoundError:
        print(f"{inspector_file} found, cannot move inspector file")

  if not dry_run:
    print("\nSUMMARY")
    print("--------------------------------")
    print("Sucessfully generated NWBs for:")
    print(*successes, sep="\n")
    print("Failed to generate NWBs for: ")
    print(*failures, sep="\n")


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument("name",
                      type=str,
                      help='Name of subject whose metadata is being updated')
  parser.add_argument(
      "--dates",
      type=int,
      nargs="+",
      help=
      'Dates of raw data to be converted. If unspecified, all dates will be converted to NWB'
  )
  parser.add_argument(
      "--excluded",
      action='append',
      help='Dates of raw data files to be excluded from NWB conversion.')

  args = parser.parse_args()

  subj = args.name
  if subj == 'teddy' or subj == 'timothy':
    data_path = "/home/gl-willow/banyan/raw/gabby/"
  else:
    data_path = "/home/gl-willow/banyan/raw/anna/"

  make_nwbs(data_path,
            args.name,
            dates=args.dates,
            excluded_dates=args.excluded,
            dry_run=False)


if __name__ == "__main__":
  main()
