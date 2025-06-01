(ns kovasap.nightly-nwb
  (:require
   [dk.ative.docjure.spreadsheet :as ss]
   [clojure.string :as string]
   [clj-yaml.core :as yaml]
   [postal.core :refer [send-message]]
   [clojure.java.shell :refer [sh]]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))


(def temp-spreadsheet-filepath "out.xlsx")

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

(defn populate-yaml-data
  [template-yaml-data parsed-sheet-data raw-data-file-paths])

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
    (if (= exit 0)
      (throw Exception. err)
      temp-spreadsheet-filepath)))


(defn get-raw-file-paths
  [path-to-raw-files]
  ; TODO turn these into strings instead of java files
  (file-seq (clojure.java.io/file path-to-raw-files)))

(def nightly-nwb-email "")

(defn send-error-email!
  [email-to-notify error-text options]
  (send-message {:from nightly-nwb-email
                 :to [email-to-notify]
                 :subject "Nightly NWB ran into issues."
                 :body (str error-text options)}))

(defn send-success-email!
  [email-to-notify options]
  (send-message {:from nightly-nwb-email
                 :to [email-to-notify]
                 :subject "Nightly NWB ran successfully."
                 :body (str options)}))

(defn generate-yaml!
  [{:keys [google-sheet-id
           yaml-template-file
           output-yaml-file
           path-to-raw-files
           email-to-notify] :as options}]
  (try
    (spit
      output-yaml-file
      (yaml/generate-string
        (populate-yaml-data
          (yaml/parse-string (slurp yaml-template-file))
          (parse-sheets (download-google-sheet! google-sheet-id))
          (get-raw-file-paths path-to-raw-files))))
    (catch Exception e (send-error-email! email-to-notify e options))
    (finally (send-success-email! email-to-notify options))))

  

(def cli-options
   ["-g" "--google-sheet-id ID" "ID for google sheet to parse."
    :default "11tDzUNBq9zIX6_9Rel__fdAUezAQzSnh5AVYzCP060c"]
   ["-f" "--path-to-raw-files DIRECTORY"
    "The path to the raw datafiles to be packaged into the NWB file."]
   ["-y" "--yaml-template-file FILE" "Template yaml file to update."
    :default "template.yaml"
    :validate [#(string/ends-with? % ".yaml") "Must be a .yaml file."]]
   ["-o" "--output-yaml-file FILE" "Output yaml file path."
    :default "out.yaml"
    :validate [#(string/ends-with? % ".yaml") "Must be a .yaml file."]]
   ["-e" "--email-to-notify EMAIL" "Email address to send notification emails to."
    :default ""
    :validate [#(re-matches #".+\@.+\..+" email) "Must be a valid email."]]
   ["-h" "--help"])

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

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "generate-yaml"  (generate-yaml! options)))))
