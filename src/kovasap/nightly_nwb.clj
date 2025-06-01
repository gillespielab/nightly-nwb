(ns kovasap.nightly-nwb
  (:require
   [dk.ative.docjure.spreadsheet :as ss]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

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

(defn -main
 "I don't do a whole lot ... yet."
 [& args])

(def cli-options
   ["-s" "--spreadsheet-file FILE" "Spreadsheet file to parse."
    :default "out.xlsx"
    :validate [#(string/ends-with? % ".xlsx") "Must be an .xlsx file."]]
   ["-y" "--yaml-template-file FILE" "Template yaml file to update."
    :default "template.yaml"
    :validate [#(string/ends-with? % ".yaml") "Must be a .yaml file."]]
   ["-e" "--email-to-notify EMAIL" "Email address to send notification emails to."
    :default ""
    :validate [#(re-matches #".+\@.+\..+" email) "Must be a valid email."]]
   ["-h" "--help"])

(defn usage [options-summary]
  (->> ["Nightly NWB file generator."
        ""
        "Usage: nightly-nwb [options]"
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
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
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
        "start"  (server/start! options)
        "stop"   (server/stop! options)
        "status" (server/status! options)))))
