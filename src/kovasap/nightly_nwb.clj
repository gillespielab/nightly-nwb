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

;; The :required specification provides the name shown in the usage summary
;; for the argument that an option expects. It is only needed when the long
;; form specification of the option is not given, only the short form. In
;; addition, :id must be specified to provide the internal keyword name for
;; the option. If you want to indicate that an option itself is required,
;; you can use the :missing key to provide a message that will be shown
;; if the option is not present.

;; The :default values are applied first to options. Sometimes you might want
;; to apply default values after parsing is complete, or specifically to
;; compute a default value based on other option values in the map. For those
;; situations, you can use :default-fn to specify a function that is called
;; for any options that do not have a value after parsing is complete, and
;; which is passed the complete, parsed option map as it's single argument.
;; :default-fn (constantly 42) is effectively the same as :default 42 unless
;; you have a non-idempotent option (with :update-fn or :assoc-fn) -- in which
;; case any :default value is used as the initial option value rather than nil,
;; and :default-fn will be called to compute the final option value if none was
;; given on the command-line (thus, :default-fn can override :default)
;; Note: validation is *not* performed on the result of :default-fn (this is
                                                                          ;; an open issue for discussion and is not currently considered a bug).

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
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
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"start" "stop" "status"} (first arguments)))
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
        "start"  (server/start! options)
        "stop"   (server/stop! options)
        "status" (server/status! options)))))
