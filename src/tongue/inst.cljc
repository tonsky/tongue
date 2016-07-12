(ns tongue.inst
  (:require
    [clojure.string :as str]
    #?(:clj [clojure.future :refer [simple-keyword? inst?]])
    [tongue.macro :as macro]
    #?(:clj [clojure.spec :as spec]))
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
     :cljs (.getHours c)))


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
    :hour24-padded   (pad2 (hour24 c))
    :hour24          (hour24 c)
    :hour12-padded   (pad2 (hour12 c))
    :hour12          (hour12 c)
    :day-period      (nth (:day-periods strings) (if (< (hour24 c) 12) 0 1))
    :minutes-padded  (pad2 (minutes c))
    :minutes         (minutes c)
    :seconds-padded  (pad2 (seconds c))
    :seconds         (seconds c)
    :milliseconds    (pad3 (milliseconds c))
    :weekday-long    (nth (:weekdays-long strings)   (day-of-week c))
    :weekday-short   (nth (:weekdays-short strings)  (day-of-week c))
    :weekday-narrow  (nth (:weekdays-narrow strings) (day-of-week c))
    :weekday-numeric (inc (day-of-week c))
    :day-padded      (pad2 (day-of-month c))
    :day             (day-of-month c)
    :month-long      (nth (:months-long strings) (month c))
    :month-short     (nth (:months-short strings) (month c))
    :month-narrow    (nth (:months-narrow strings) (month c))
    :month-numeric-padded (pad2 (inc (month c)))
    :month-numeric   (inc (month c))
    :year            (year c)
    :year-2digit     (pad2 (mod (year c) 100))
    :era-long        (nth (:eras-long strings)   (era c))
    :era-short       (nth (:eras-short strings)  (era c))
    (if (string? token)
      token
      (str "<" (name token) ">"))))


#?(:clj (def ^:private UTC (java.util.TimeZone/getTimeZone "UTC")))


(macro/with-spec
  (spec/def ::weekdays-narrow (spec/coll-of string? :count 7))
  (spec/def ::weekdays-short  (spec/coll-of string? :count 7))
  (spec/def ::weekdays-long   (spec/coll-of string? :count 7))
  (spec/def ::months-narrow   (spec/coll-of string? :count 12))
  (spec/def ::months-short    (spec/coll-of string? :count 12))
  (spec/def ::months-long     (spec/coll-of string? :count 12))
  (spec/def ::day-periods     (spec/coll-of string? :count 2))
  (spec/def ::eras-short      (spec/coll-of string? :count 2))
  (spec/def ::eras-long       (spec/coll-of string? :count 2))

  (spec/def ::template string?)
  (spec/def ::strings
    (spec/keys :opt-un [::weekdays-narrow ::weekdays-short ::weekdays-long ::months-narrow ::months-short ::months-long ::day-periods ::eras-short ::eras-long])))




(defn formatter
  [template strings]

  (macro/with-spec
    (spec/assert ::template template)
    (spec/assert ::strings strings))
  
  (let [tokens (->> (re-seq #"(?:<([^<> ]+)>|[<]|[^<]*)" template)
                    (map (fn [[string code]] (if code (keyword code) string))))]
    #?(:clj
        (fn format
          ([t] 
            (macro/with-spec
              (spec/assert inst? t))
            (format t UTC))
          ([t tz]
            (macro/with-spec
              (spec/assert inst? t)
              (spec/assert #(instance? java.util.TimeZone %) tz))
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
            (macro/with-spec
              (spec/assert inst? t))
            (reduce (fn [s token] (str s (format-token strings token t))) "" tokens))
          ([t tz-offset-min]
            (macro/with-spec
              (spec/assert inst? t)
              (spec/assert #(spec/int-in-range? -1440 1440 %)  tz-offset-min))
            (let [default-offset-min (- (.getTimezoneOffset t))
                  corrected-t        (if (== default-offset-min tz-offset-min)
                                       t
                                       (js/Date. (+ (.getTime t)
                                                    (* 60000 (- tz-offset-min default-offset-min)))))]
              (format corrected-t)))))))
