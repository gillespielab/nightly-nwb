(ns kovasap.nightly-nwb
  (:require
   [dk.ative.docjure.spreadsheet :as ss]
   [clojure.string :as string]
   [clj-yaml.core :as yaml]
   [postal.core :refer [send-message]]
   [clojure.java.shell :refer [sh]]
   [clojure.java.io]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))


(def temp-spreadsheet-filepath "out.xlsx")

(def default-template-yaml-filepath
  "{{path-to-raw-files}}/{{experimenter}}/{{subject}}/{{subject}}_metadata.yml")

(def default-output-yaml-filepath
  "{{path-to-raw-files}}/{{experimenter}}/{{subject}}/{{date}}/{{date}}_{{subject}}_metadata.yml")

(def behavior-sheet-name
  "{{subject}}_behavior")

(def adjusting-sheet-name
  "{{subject}}_adjusting")

(defn replace-placeholders
  [template-string arg-map]
  (string/replace template-string
                  #"\{\{([a-zA-Z\-]+)\}\}"
                  (fn [[_match placeholder]]
                    (str (get arg-map (keyword placeholder))))))
   
(replace-placeholders "My name is {{name}} and I am {{age}} years old." {:name "Bob" :age 25})

(defn zip [& colls]
  (partition (count colls) (apply interleave colls)))

(defn get-col-header-values
  [sheet]
  (map #(.getStringCellValue %) (ss/cell-seq (first (ss/row-seq sheet)))))

(defn get-rows-data
  "Returns data like
  [{:col-header 'first' :row-header 'date' :value 'val' :color [a, r, g, b]} ...]
  "
  [sheet]
  (let [col-header-values (get-col-header-values sheet)]
    (flatten
      (for [row (rest (ss/row-seq sheet))]
        (for [[col-header cell] (zip (rest col-header-values)
                                     (rest (ss/cell-seq row)))]
          {:col-header col-header
           :row-header (.getStringCellValue (first (ss/cell-seq row)))
           :value      (.toString cell)
           :color      (as-> cell c
                         (.getCellStyle c)
                         (.getFillBackgroundXSSFColor c)
                         (if (nil? c) nil (.getARGB c)))})))))

(defn parse-sheets
  [workbook-path]
  (into {} (for [sheet (ss/sheet-seq (ss/load-workbook workbook-path))]
             [(.getSheetName sheet) sheet])))

(defn download-google-sheet!
  "Returns true if the download was successful, false otherwise."
  [google-sheet-id]
  (let [{:keys [out exit err]} (sh "gdrive"
                                   "files"
                                   "export"
                                   google-sheet-id
                                   temp-spreadsheet-filepath
                                   "--overwrite")]
    (println out)
    (println err)
    (println "Done exporting spreadsheet")
    (if (= exit 0)
      temp-spreadsheet-filepath
      (throw (Exception. err)))))


(defn get-raw-file-paths
  [path-to-raw-files]
  ; TODO turn these into strings instead of java files
  (file-seq (clojure.java.io/file path-to-raw-files)))

; TODO update this so that it updates the yaml instead of just copying the
; template. 
(defn generate-single-yaml
  [experimenter subject date behavior-data adjusting-data template-yaml-data data-filepaths]
  template-yaml-data)

; TODO update this so that it actually lists the files that need generation.
(defn determine-dates-to-process
  "Return list of dates formatted like YYYYMMDD for which there are no yaml files in their directory."
  [experimenter subject path-to-raw-files]
  ["20250602"])

(defn write-yaml-data-to-file
  [yaml-data filepath]
  (spit filepath (yaml/generate-string yaml-data)))

(defn generate-yaml!
  "Returns map like {:success? true :failure-message ''}."
  [{:keys [google-sheet-id
           experimenter
           subject
           yaml-template-file
           output-yaml-file
           path-to-raw-files]
    :as   options}]
  (let [parsed-sheets (parse-sheets (download-google-sheet! google-sheet-id))]
    (println "Starting date processing...")
    (for [date (determine-dates-to-process experimenter
                                           subject
                                           path-to-raw-files)
          :let [options (assoc options :date date)]]
      (do (println (str "Processing data for " date "..."))
          (try (write-yaml-data-to-file
                 (generate-single-yaml
                   experimenter
                   subject
                   date
                   (get parsed-sheets
                        (replace-placeholders behavior-sheet-name options))
                   (get parsed-sheets
                        (replace-placeholders adjusting-sheet-name options))
                   (yaml/parse-string
                     (slurp (replace-placeholders yaml-template-file options)))
                   (get-raw-file-paths path-to-raw-files))
                 (replace-placeholders output-yaml-file options))
               (catch Exception e {:success? false :failure-message e})
               (finally {:success? true}))))))

(def cli-options
   [["-g" "--google-sheet-id ID" "ID for google sheet to parse."
     :default "1QxgE1NmOHCZbkmkR0kq1E03szCnmwS7VdtZwE8eyrUY"]
    ["-f" "--path-to-raw-files DIRECTORY"
     "The path to the raw datafiles."
     :default "banyan/raw/"]
    ["-s" "--subject SUBJECT" "Subject to process data for."]
    ["-e" "--experimenter EXPERIMENTER" "Experimenter to process data for."]
    ["-y" "--yaml-template-file FILE" "Template yaml file to update."
     :default default-template-yaml-filepath
     :validate [#(string/ends-with? % ".yml") "Must be a .yml file."]]
    ["-o" "--output-yaml-file FILE" "Output yaml file path."
     :default default-output-yaml-filepath
     :validate [#(string/ends-with? % ".yml") "Must be a .yml file."]]
    ["-n" "--email-to-notify EMAIL" "Email address to send notification emails to."
     :default nil
     :validate [#(re-matches #".+\@.+\..+" %) "Must be a valid email."]]
    ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Nightly NWB file generator."
        ""
        "Usage: nightly-nwb [options] action"
        ""
        "Actions:"
        "  generate-yaml    Generate a yaml file from a given template."
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      (and (= 1 (count arguments))
           (#{"generate-yaml"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))


(def nightly-nwb-email "")

(defn send-email-report!
  [email-to-notify status-map]
  (send-message {:from nightly-nwb-email
                 :to [email-to-notify]
                 :subject "Nightly NWB ran into issues."
                 :body ""}))

(defn report-errors!
  [email-to-notify status-map]
  (if (nil? email-to-notify)
    (println status-map)
    (send-email-report!
      email-to-notify
      status-map)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (report-errors!
      (:email-to-notify options)
      (if exit-message
        (exit (if ok? 0 1) exit-message)
        (case action
          "generate-yaml"  (generate-yaml! options)))))
    ; Added so that our use of sh does not hang the program.
  (shutdown-agents))
