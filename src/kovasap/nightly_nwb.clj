(ns kovasap.nightly-nwb
  (:require
   [dk.ative.docjure.spreadsheet :as ss])
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

(defn -main
 "I don't do a whole lot ... yet."
 [& args]
 (greet {:name (first args)}))

(let [wb (ss/load-workbook "out.xlsx")]
 (get-rows-data (first (ss/sheet-seq wb))))
