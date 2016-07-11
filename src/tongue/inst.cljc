(ns tongue.inst
  (:require
    [clojure.string :as str]
    #?(:clj [clojure.future :refer [simple-keyword? inst?]])
    [#?(:clj clojure.spec :cljs cljs.spec) :as spec])
  #?(:clj
      (:import
        [java.util Calendar])))


(defn- pad2 [i]
  (if (< i 10)
    (str "0" i)
    (str i)))


(defn- pad3 [i]
  (cond
    (< i 10)  (str "00" i)
    (< i 100) (str "0" i)
    :else     (str i)))


(defn hour24 [c]
  #?(:clj  (.get ^Calendar c Calendar/HOUR_OF_DAY)
     :cljs (.getHours t)))


(defn hour12 [c]
  (-> (hour24 c) (+ 11) (mod 12) inc))


(defn minutes [c]
  #?(:clj  (.get ^Calendar c Calendar/MINUTE)
     :cljs (.getMinutes c)))


(defn seconds [c]
  #?(:clj  (.get ^Calendar c Calendar/SECOND)
     :cljs (.getSeconds c)))


(defn milliseconds [c]
  #?(:clj  (.get ^Calendar c Calendar/MILLISECOND)
     :cljs (.getMilliseconds c)))


(defn day-of-week [c] ;; Sunday => 0, ...
  #?(:clj  (dec (.get ^Calendar c Calendar/DAY_OF_WEEK))
     :cljs (.getDay c)))
  
  
(defn day-of-month [c]
  #?(:clj  (.get ^Calendar c Calendar/DAY_OF_MONTH)
     :cljs (.getDate c)))


(defn month [c] ;; January => 0, ...
  #?(:clj  (.get ^Calendar c Calendar/MONTH)
     :cljs (.getMonth c)))


(defn year [c] ;; January => 0, ...
  #?(:clj  (.get ^Calendar c Calendar/YEAR)
     :cljs (+ 1900 (.getYear c))))


(defn era [c] ;; BC => 0, AD => 1
  (if (< (year c) 1)
    0 1))


(defn format-token [strings token c]
  (case token
    ;; 24 hour (0-23)
    :HH    (pad2 (hour24 c))
    :H     (hour24 c)
    ;; 12 hour (1-12)
    :hh    (pad2 (hour12 c))
    :h     (hour12 c)
    ;; AM/PM
    :a     (nth (:day-periods strings) (if (< (hour24 c) 12) 0 1))
    ;; minutes
    :mm    (pad2 (minutes c))
    :m     (minutes c)
    ;; seconds
    :ss    (pad2 (seconds c))
    :s     (seconds c)
    ;; milliseconds
    :S     (pad3 (milliseconds c))
    ;; day of week (Sunday => 1, Monday => 2, ...)
    :eeeee (nth (:weekdays-narrow strings) (day-of-week c))
    :eeee  (nth (:weekdays-long strings)   (day-of-week c))
    :eee   (nth (:weekdays-short strings)  (day-of-week c))
    :ee    (pad2 (inc (day-of-week c)))
    :e     (inc (day-of-week c))
    ;; day of month (1-31)
    :dd    (pad2 (day-of-month c))
    :d     (day-of-month c)
    ;; month (Jan => 1, Feb => 2, ...)
    :MMMMM (nth (:months-narrow strings) (month c))
    :MMMM  (nth (:months-long strings) (month c))
    :MMM   (nth (:months-short strings) (month c))
    :MM    (pad2 (inc (month c)))
    :M     (inc (month c))
    ;; year
    :yyyy  (year c)
    :yy    (pad2 (mod (year c) 100))
    ;; era (BC/AD)
    :GGGGG (nth (:eras-narrow strings) (era c))
    :GGGG  (nth (:eras-long strings)   (era c))
    :GGG   (nth (:eras-short strings)  (era c))
    ;; otherwise
    token))


#?(:clj (def ^:private UTC (java.util.TimeZone/getTimeZone "UTC")))



(spec/def ::weekdays-narrow (spec/coll-of string? :count 7))
(spec/def ::weekdays-short  (spec/coll-of string? :count 7))
(spec/def ::weekdays-long   (spec/coll-of string? :count 7))
(spec/def ::months-narrow   (spec/coll-of string? :count 12))
(spec/def ::months-short    (spec/coll-of string? :count 12))
(spec/def ::months-long     (spec/coll-of string? :count 12))
(spec/def ::day-periods     (spec/coll-of string? :count 2))
(spec/def ::eras-narrow     (spec/coll-of string? :count 2))
(spec/def ::eras-short      (spec/coll-of string? :count 2))
(spec/def ::eras-long       (spec/coll-of string? :count 2))

(spec/def ::pattern string?)
(spec/def ::strings
  (spec/keys :opt-un [::weekdays-narrow ::weekdays-short ::weekdays-long ::months-narrow ::months-short ::months-long ::day-periods ::eras-narrow ::eras-short ::eras-long]))


(defn formatter
  [pattern strings]
  
  (spec/assert ::pattern pattern)
  (spec/assert ::strings strings)
  
  (let [tokens (->> (re-seq #"(?:'([^']*)'|HH|H|hh|h|a|mm|m|ss|s|S|e{1,5}|dd|d|M{1,5}|yyyy|yy|G{3,5}|([^HhamsSedMyG']+)|([^']))" pattern)
                    (map (fn [[a b c d]]
                           (or b c d (keyword a)))))]
    #?(:clj
        (fn format
          ([t] 
            (spec/assert inst? t)
            (format t UTC))
          ([t tz]
            (spec/assert inst? t)
            (spec/assert #(instance? java.util.TimeZone %) tz)
            (let [cal (doto (Calendar/getInstance)
                        (.setTimeZone tz)
                        (.setTime t))
                  sb (StringBuilder.)]
              (->> tokens
                   (reduce (fn [sb token]
                             (.append sb ^String (format-token strings token cal)))
                           sb)
                   (.toString)))))
       :cljs
        (fn format
          ([t]
            (spec/assert inst? t)
            (reduce (fn [s token] (str s (format-token strings token t))) "" tokens))
          ([t tz-offset-min]
            (spec/assert inst? t)
            (spec/assert #(spec/int-in-range? -1440 1440 %)  tz-offset-min)
            (let [default-offset-min (- (.getTimezoneOffset t))
                  corrected-t        (if (== default-offset-min tz-offset-min)
                                       t
                                       (js/Date. (+ (.getTime t)
                                                    (* 60000 (- tz-offset-min default-offset-min)))))]
              (format corrected-t)))))))
