(ns kovasap.nightly-nwb
  (:require
    [dk.ative.docjure.spreadsheet :as ss])
  (:gen-class))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))

(let [wb (ss/load-workbook "out.xlsx")]
  (.getARGB (.getFillBackgroundXSSFColor (first (ss/get-row-styles (last (ss/row-seq (first (ss/sheet-seq wb)))))))))
