(ns gillespielab.nightly-nwb
  (:require
   [dk.ative.docjure.spreadsheet :as ss]
   [clojure.string :as string]
   [clj-yaml.core :as yaml]
   [postal.core :refer [send-message]]
   [clojure.java.shell :refer [sh]]
   [clojure.java.io :as io]
   [clojure.tools.cli :refer [parse-opts]])
  (:import [java.text SimpleDateFormat]
           [org.apache.poi.ss.usermodel DateUtil CellType])
  (:gen-class))


(def temp-spreadsheet-filepath "out.xlsx")

(def path-to-subject-dir
  "{{root-data-dir}}/raw/{{experimenter}}/{{subject}}/")
  
(def default-template-yaml-filepath
  (str path-to-subject-dir "{{subject}}_metadata.yml"))

(def default-output-yaml-filepath
  (str path-to-subject-dir "{{date}}/{{date}}_{{subject}}_metadata.yml"))

(def default-output-nwb-dir
  "{{root-data-dir}}/nwb/raw/")

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

(defn zip [& colls]
  (partition (count colls) (apply interleave colls)))

(defn get-cell-value
  [cell]
  (cond (nil? cell) nil
        (and 
          (= CellType/NUMERIC (.getCellType cell))
          (DateUtil/isCellDateFormatted cell))
        (.format (SimpleDateFormat. "yyyyMMdd") (.getDateCellValue cell))
        (= CellType/NUMERIC (.getCellType cell)) (.getNumericCellValue cell)
        (empty? (.toString cell)) nil
        :else (.toString cell)))

(defn get-col-header-values
  "Gets column header values for the given sheet.  The given column-row-idx 
  will be used to determine which row should be considered the header row."
  ([sheet column-row-idx]
   (map get-cell-value (ss/cell-seq (nth (ss/row-seq sheet) column-row-idx))))
  ([sheet] (get-col-header-values sheet 0)))

(defn get-rows-data
  "Returns data like
  [{:col-header 'first' :row-header 'date' :value 'val' :color [a, r, g, b]} ...]
  "
  [sheet column-row-idx]
  (let [col-header-values (get-col-header-values sheet column-row-idx)]
    (flatten
      (for [row  (remove nil? (rest (ss/row-seq sheet)))
            :let [row-header (get-cell-value (first (ss/cell-seq row)))]
            :when (not (nil? row-header))]
        (for [[col-header cell] (zip (rest col-header-values)
                                     (rest (ss/cell-seq row)))
              :when (not (nil? col-header))]
          {:col-header col-header
           :row-header row-header
           :value      (get-cell-value cell)
           :color      (if (nil? cell)
                         nil
                         (as-> cell c
                           (.getCellStyle c)
                           (.getFillBackgroundXSSFColor c)
                           (if (nil? c) nil (vec (.getARGB c)))))})))))

(defn get-sheets-by-name
  [workbook-path]
  (into {} (for [sheet (ss/sheet-seq (ss/load-workbook workbook-path))]
             [(.getSheetName sheet) sheet])))

(defn download-google-sheet!
  "Returns path to output xlsx file."
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
  [{:keys [date root-data-dir]}]
  (filter
    #(string/includes? (.getAbsolutePath %) date)
    (file-seq (io/file root-data-dir))))

(defn get-session-number
  [date behavior-data]
  (first (filter (fn [{:keys [col-header row-header]}]
                   (and (= col-header "session")
                        (= row-header date)))
                 behavior-data)))

(defn get-session-id
  [subject date behavior-data]
  (format "%s_%02d"
          subject
          (int (:value (get-session-number date behavior-data)))))

(def state-script-log-regex
  #"(.+)_(.+)_(.+)_(.+)\.stateScriptLog") 

(defn state-script-log-file->data
  [state-script-log-file]
  (if-some [[_whole-filename-match _date _subject task-epoch task-code]
            (re-matches state-script-log-regex
                        (.getName state-script-log-file))]
   {:name (str "statescript_" task-code)
    :description (str "Statescript log " task-code)
    :path (.getAbsolutePath state-script-log-file)
    :task_epochs (Integer/parseInt task-epoch)}
   nil))

(defn generate-associated-files
  [data-filepaths]
  (sort-by :task_epochs
           (remove nil?
             (map #(state-script-log-file->data %) data-filepaths))))

(def video-file-regex
  #"(.+)_(.+)_(.+)_(.+)\.(.+)\.h264") 

(defn video-file->data
  [video-file task-letter-to-camera-ids]
  (if-some [[whole-filename-match _date _subject task-epoch task-code]
            (re-matches video-file-regex (.getName video-file))]
    {:name whole-filename-match
     :camera_id (get task-letter-to-camera-ids (subs task-code 0 1))
     :task_epochs (Integer/parseInt task-epoch)}
    nil))

(defn generate-associated-video-files
  [data-filepaths task-letter-to-camera-ids]
  (sort-by :task_epochs
           (remove nil?
             (map #(video-file->data % task-letter-to-camera-ids)
               data-filepaths))))


(defn task-name->letter
  [task-name]
  (case task-name
    "Sleep" "s" 
    "r"))


(defn get-task-letter-to-camera-ids
  [tasks]
  (into {}
        (for [{:keys [task_name camera_id]} tasks]
          [(task-name->letter task_name) (first camera_id)])))

(def rec-regex
  #"(.+)_(.+)_(.+)_(.+)\.rec") 

(defn rec-file-to-letter-epoch
  [rec-file]
  (if-some [[_whole-filename-match _date _subject task-epoch task-code]
            (re-matches rec-regex
                        (.getName rec-file))]
    {:task-letter (subs task-code 0 1) :epoch (Integer/parseInt task-epoch)}
    nil))

(defn get-task-letter-to-epochs
  [data-filepaths]
  (as-> data-filepaths d 
    (map rec-file-to-letter-epoch d)
    (remove nil? d)
    (group-by :task-letter d)
    (update-vals d #(map :epoch %))))
          

(defn add-epochs-to-task
  [{:keys [task_name] :as task} task-letter-to-epochs]
  (assoc task
    :task_epochs (sort (get task-letter-to-epochs
                            (task-name->letter task_name)))))

(defn update-task-data
  [task-data task-letter-to-epochs]
  (sort-by #(first (:camera_id %))
           (map #(add-epochs-to-task % task-letter-to-epochs) task-data)))

(defn color->location
  [color]
  ; I tried case here, but it fails to match the vectors sometimes for a
  ; reason i don't understand.
  (cond
    ; Green
    (= color [-1 -74 -41 -88]) "ca1"
    ; Blue
    (= color [-1 -97 -59 -24]) "can1ref"
    ; Purple
    (= color [-1 -76 -89 -42]) "can2ref"
    ; Everything else 
    :else "''"))

(defn cell->electrode-group
  [{:keys [col-header color]}]
  ; dec since yaml electrodes are indexed by 0, but spreadsheet is indexed by
  ; 1!
  {:id (dec col-header)  
   :location (color->location color)})

(defn clean-spreadsheet-number
  [n]
  (if (number? n)
    (int n)
    (try (Integer/parseInt n) (catch Exception _ nil))))

(defn generate-electrode-groups
  [date adjusting-data]
  (as-> adjusting-data d
   (filter #(= date (:row-header %)) d)
   (map #(update % :col-header clean-spreadsheet-number) d)
   (filter #(number? (:col-header %)) d)
   (map cell->electrode-group d)
   (sort-by :id d)))

(defn merge-maps
  "Merges maps with matching id-key from maps2 into matching maps1 entries.

  Non matching maps2 entries will be ignored."
  [maps1 maps2 id-key]
  (let [maps2-by-key (group-by id-key maps2)]
    (into []
          (for [m maps1]
            (apply merge m (get maps2-by-key (id-key m)))))))

(defn update-electrode-groups
  [existing-electrode-groups date adjusting-data]
  (sort-by :id
           (merge-maps existing-electrode-groups
                       (generate-electrode-groups date adjusting-data)
                       :id)))

(defn extract-channel-id
  [s]
  (try
    (Integer/parseInt
      (last (re-matches #"ch(\d)" s)))
    (catch Exception _ nil)))

(defn generate-channel-map-from-dead-channels
  [adjusting-data]
  (as-> adjusting-data d
    (filter #(= "dead channels" (:row-header %)) d)
    (map #(update % :col-header clean-spreadsheet-number) d)
    (filter #(number? (:col-header %)) d)
    (remove #(nil? (extract-channel-id (:value %))) d)
    (map (fn [{:keys [col-header value]}]
           {:ntrode_id col-header
            :electrode_group_id (dec col-header)
            :bad_channels [(dec (extract-channel-id value))]})
      d)
    (sort-by :ntrode_id d)))

(defn extract-ref-channel-id
  [s]
  (try
    (Integer/parseInt
     (last (re-matches #"ref_ch: ch(\d)" s)))
    (catch Exception _ nil)))

(defn generate-channel-map-from-ref-ch
  [date adjusting-data]
  (as-> adjusting-data d
    (filter #(= date (:row-header %)) d)
    (map #(update % :col-header clean-spreadsheet-number) d)
    (filter #(number? (:col-header %)) d)
    (remove #(nil? (extract-ref-channel-id (:value %))) d)
    (map (fn [{:keys [col-header value]}]
           {:ntrode_id col-header
            :electrode_group_id (dec col-header)
            :bad_channels [(dec (extract-ref-channel-id value))]})
      d)
    (sort-by :ntrode_id d)))

(defn update-ntrode-electrode-group-channel-map
  [existing-channel-map date adjusting-data]
  (sort-by :ntrode_id
           (merge-maps
             existing-channel-map
             (merge-maps
               (generate-channel-map-from-dead-channels adjusting-data)
               (generate-channel-map-from-ref-ch date adjusting-data)
               :ntrode_id)
             :ntrode_id)))
 
(defn generate-single-yaml-data
  [{:keys [subject date] :as data-spec}
   behavior-data
   adjusting-data
   template-yaml-data
   data-filepaths]
  (->
    template-yaml-data
    (assoc :default_header_file_path
           (str (.getAbsolutePath (java.io.File. ""))
                "/"
                (replace-placeholders path-to-subject-dir data-spec)
                date
                "/"))
    (update :electrode_groups #(update-electrode-groups % date adjusting-data))
    (update :ntrode_electrode_group_channel_map
            #(update-ntrode-electrode-group-channel-map % date adjusting-data))
    (update :tasks
            #(update-task-data % (get-task-letter-to-epochs data-filepaths)))
    (assoc :session_id (get-session-id subject date behavior-data))
    (assoc :associated_files (generate-associated-files data-filepaths))
    (assoc :associated_video_files (generate-associated-video-files
                                     data-filepaths
                                     (get-task-letter-to-camera-ids
                                       (:tasks template-yaml-data))))))

(defn nwb-filepath
  [data-spec output-nwb-dir]
  (str (replace-placeholders output-nwb-dir data-spec)
       (:experimenter data-spec)
       (:date data-spec)
       ".nwb"))

(defn determine-dates-to-process
  "Return list of dates formatted like YYYYMMDD for which nwbs have not been created yet."
  [data-spec output-nwb-dir]
  ; TODO make sure this returns just the date strings
  (as-> data-spec d
    (replace-placeholders path-to-subject-dir d)
    (.listFiles (io/file d))
    (filter #(.isDirectory %) d)
    (map #(.getName %) d)
    (filter #(not (.exists (io/file (nwb-filepath (assoc data-spec :date %)
                                                  output-nwb-dir))))
      d)))
    ; TODO Check the associated spreadsheet (i.e. the UW_Aging_Cohort
    ; spreadsheet in teddy's case) for whether this new date should be
    ; processed: check in the teddy_behavior tab that this date (20250602) has
    ; an associated session number that is not equal to 0

(defn write-yaml-data-to-file
  [yaml-data filepath]
  (spit filepath (yaml/generate-string yaml-data)))

(def DataSpec
  [:map
   [:date :string]
   [:experimenter :string]
   [:subject :string]
   [:path-to-raw-files :string]])

(defn generate-single-yaml!
  [data-spec spreadsheet-file template-yaml-file output-yaml-file]
  (let [sheets-by-name (get-sheets-by-name spreadsheet-file)
        real-yaml-output-file (replace-placeholders output-yaml-file data-spec)]
    (println (str "Writing yaml file to " real-yaml-output-file "..."))
    (write-yaml-data-to-file
      (generate-single-yaml-data
        data-spec
        (get-rows-data (get sheets-by-name
                            (replace-placeholders behavior-sheet-name
                                                  data-spec))
                       0)
        (get-rows-data (get sheets-by-name
                            (replace-placeholders adjusting-sheet-name
                                                  data-spec))
                       3)
        (yaml/parse-string (slurp (replace-placeholders template-yaml-file
                                                        data-spec)))
        (get-raw-file-paths data-spec))
      real-yaml-output-file)))

; TODO test this
(defn generate-single-nwb!
  "Returns path to output nwb file."
  [{:keys [date] :as data-spec}
   output-nwb-dir
   &
   {:keys [dry-run] :or {dry-run false}}]
  (let [arguments (remove nil?
                    ["python"
                     "nwb_conversion/single_nwb_conversion.py"
                     "--date"
                     date
                     "--output_dir"
                     (replace-placeholders output-nwb-dir data-spec)
                     (if dry-run "--dry_run" nil)
                     "--data_directory"
                     (replace-placeholders path-to-subject-dir data-spec)])]
    (println "Executing " (string/join " " arguments))
    (let [{:keys [out exit err]} (apply sh arguments)]
      (println out)
      (println err)
      (println "Done generating nwb")
      (if (= exit 0) out (throw (Exception. err))))))

(defn generate-yaml-then-nwb!
  "Returns map like {:success? true :failure-message ''}."
  [{:keys [google-sheet-id
           experimenter
           subject
           dates
           yaml-only
           template-yaml-file
           output-yaml-file
           output-nwb-dir
           root-data-dir]
    :as   options}]
  (println "Starting date processing...")
  (for [date (if (empty? dates)
               (determine-dates-to-process options output-nwb-dir)
               dates)
        :let [data-spec (assoc options :date date)
              real-yaml-template-path (replace-placeholders template-yaml-file
                                                            data-spec)]]
    (if (not (.exists (io/file real-yaml-template-path)))
      (println (str "No yaml template file found at "
                    real-yaml-template-path
                    ", not processing date "
                    date
                    "."))
      (do (println (str "Processing data for " date "..."))
          (try (generate-single-yaml! data-spec
                                      (download-google-sheet! google-sheet-id)
                                      template-yaml-file
                                      output-yaml-file)
               (if (not yaml-only)
                 (generate-single-nwb! data-spec output-nwb-dir)
                 nil)
               (catch Exception e {:success? false :failure-message e})
               (finally {:success? true}))))))

(def cli-options
   [["-g" "--google-sheet-id ID" "ID for google sheet to parse."
     :default "1QxgE1NmOHCZbkmkR0kq1E03szCnmwS7VdtZwE8eyrUY"]
    ["-d" "--root-data-dir DIRECTORY"
     "The path to the raw datafiles."
     :default "banyan/"]
    ["-y" "--yaml-only" "Only generate the yaml file, not the NWB"
     :default false]
    ["-s" "--subject SUBJECT" "Subject to process data for."]
    ["-e" "--experimenter EXPERIMENTER" "Experimenter to process data for."]
    ["-d" "--dates DATE"
     (str "Date(s) to process data for, separated by commas. If not specified, "
      "will automatically process data for dates that do not already have a "
      "yaml file generated for them. ")
     :default []
     :parse-fn #(string/split % #",")]
    ["-y" "--template-yaml-file FILE" "Template yaml file to update."
     :default default-template-yaml-filepath
     :validate [#(string/ends-with? % ".yml") "Must be a .yml file."]]
    ["-o" "--output-yaml-file FILE" "Output yaml file path."
     :default default-output-yaml-filepath
     :validate [#(string/ends-with? % ".yml") "Must be a .yml file."]]
    ["-w" "--output-nwb-dir DIR" "Output nwb directory."
     :default default-output-nwb-dir]
    ["-n" "--email-to-notify EMAIL"
     "Email address to send notification emails to. Not working yet."
     :default nil
     :validate [#(re-matches #".+\@.+\..+" %) "Must be a valid email."]]
    ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Nightly NWB file generator."
        ""
        "Usage: nightly-nwb [options] action"
        ""
        "Actions:"
        "  generate-yaml-then-nwb    Generate a yaml file from a given template, then generate the nwb file based on the yaml."
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
           (#{"generate-yaml-then-nwb"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))


(def nightly-nwb-email "test@nightly-nwb.com")

(defn send-email-report!
  [email-to-notify status-map]
  (send-message {:from nightly-nwb-email
                 :to [email-to-notify]
                 :subject "Nightly NWB ran into issues."
                 :body ""}))

; TODO read https://andersmurphy.com/2022/06/14/clojure-sending-emails-with-postal-and-gmail-smtp.html
; (send-email-report! "kovas.palunas@gmail.com" {})

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
          "generate-yaml-then-nwb"  (generate-yaml-then-nwb! options)))))
    ; Added so that our use of sh does not hang the program.
  (shutdown-agents))
