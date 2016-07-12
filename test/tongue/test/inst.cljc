(ns tongue.test.inst
  (:require
    [tongue.core :as tongue]
    #?(:clj  [clojure.test :refer [deftest is are testing]]
       :cljs [cljs.test :as test :refer-macros [deftest is are testing]])))


(def UTC
  #?(:clj  (java.util.TimeZone/getTimeZone "UTC")
     :cljs 0))


(def GMT+6
  #?(:clj  (java.util.TimeZone/getTimeZone "GMT+6")
     :cljs 360))


(def strings
  { :weekdays-narrow ["S" "M" "T" "W" "T" "F" "S"]
    :weekdays-short  ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]
    :weekdays-long   ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"]
    :months-narrow   ["J" "F" "M" "A" "M" "J" "J" "A" "S" "O" "N" "D"]
    :months-short    ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]
    :months-long     ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October" "November" "December"]
    :day-periods     ["AM" "PM"]
    :eras-short      ["BC" "AD"]
    :eras-long       ["Before Christ" "Anno Domini"] })


(deftest test-format-inst
  (let [inst #inst "2016-01-02T03:04:05.006"]
    (are [f r] (= r ((tongue/inst-formatter f strings) inst UTC))
      "<hour24-padded>"    "03"
      "<hour24>"           "3"
      "<hour12-padded>"    "03"
      "<hour12>"           "3"
      "<day-period>"       "AM"
      "<minutes-padded>"   "04"
      "<minutes>"          "4"
      "<seconds-padded>"   "05"
      "<seconds>"          "5"
      "<milliseconds>"     "006"
      "<weekday-long>"     "Saturday"
      "<weekday-short>"    "Sat"
      "<weekday-narrow>"   "S"
      "<weekday-numeric>"  "7"
      "<day-padded>"       "02"
      "<day>"              "2"
      "<month-long>"       "January"
      "<month-short>"      "Jan"
      "<month-narrow>"     "J"
      "<month-numeric-padded>" "01"
      "<month-numeric>"    "1"
      "<year>"             "2016"
      "<year-2digit>"      "16"
      "<era-long>"         "Anno Domini"
      "<era-short>"        "AD"))
  
  (testing "12hr time"
    (let [f (tongue/inst-formatter "<hour12-padded>:<minutes-padded> <day-period>" strings)]
      (are [i s] (= (f i UTC) s)
        #inst "2016-01-01T00:00" "12:00 AM"
        #inst "2016-01-01T00:01" "12:01 AM"
        #inst "2016-01-01T01:00" "01:00 AM"
        #inst "2016-01-01T12:00" "12:00 PM"
        #inst "2016-01-01T12:01" "12:01 PM"
        #inst "2016-01-01T13:00" "01:00 PM"
        #inst "2016-01-01T23:59" "11:59 PM")))
  
  (testing "template parsing"
    (let [format (tongue/inst-formatter "< <day> of <month-numeric> > < <hour24-padded> >" {})]
      (is (= "< 2 of 1 > < 03 >" (format #inst "2016-01-02T03:04:05.006" UTC)))))
  
  (testing "unknown key"
    (let [format (tongue/inst-formatter "<day> <unknwn>" {})]
      (is (= "2 <unknwn>" (format #inst "2016-01-02" UTC)))))
    
  (testing "timezones"
    (are [i tz s] (= ((tongue/inst-formatter "<year>-<month-numeric-padded>-<day-padded> <hour24-padded>:<minutes-padded>:<seconds-padded>" {}) i tz) s)
      #inst "2016-07-09T12:30:55" UTC   "2016-07-09 12:30:55"
      #inst "2016-07-09T12:30:55" GMT+6 "2016-07-09 18:30:55"
      #inst "2016-07-09T23:30:55" GMT+6 "2016-07-10 05:30:55")))


(deftest test-translate
  (let [dicts { :en { :inst-long  (tongue/inst-formatter "<month-long> <day>, <year> at <hour12>:<minutes-padded> <day-period>" strings)
                      :inst-subst "inst %1"
                      :tongue/format-inst (tongue/inst-formatter "<month-numeric>/<day>/<year-2digit> <hour12>:<minutes-padded> <day-period>" strings) }
                :ru { :inst-subst "момент %1" }}
        translate (tongue/build-translate dicts)]
    
    (are [l k a t] (= (apply translate l k a) t)
      :en :inst-long  [#inst "2016-07-09T01:00:00.000" UTC]   "July 9, 2016 at 1:00 AM" ;; UTC timezone
      :en :inst-long  [#inst "2016-07-09T01:00:00.000" GMT+6] "July 9, 2016 at 7:00 AM" ;; GMT+6 timezone
      ;; Expect TZ to be set to UTC in node.js/CLJS env
      :en :inst-long  [#inst "2016-07-09T01:00:00.000"] "July 9, 2016 at 1:00 AM"       ;; UTC assumed by default
      :en :inst-subst [#inst "2016-07-09T01:00:00.000"] "inst 7/9/16 1:00 AM"           ;; substitutions use :tongue/format-inst
      :ru :inst-subst [#inst "2016-07-09T01:00:00.000"] "момент 2016-07-09T01:00:00"))) ;; or default formatter

