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


(def inst-strings
  { :weekdays-narrow ["S" "M" "T" "W" "T" "F" "S"]
    :weekdays-short  ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]
    :weekdays-long   ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"]
    :months-narrow   ["J" "F" "M" "A" "M" "J" "J" "A" "S" "O" "N" "D"]
    :months-short    ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]
    :months-long     ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October" "November" "December"]
    :day-periods     ["AM" "PM"]
    :eras-narrow     ["B" "A"]
    :eras-short      ["BC" "AD"]
    :eras-long       ["Before Christ" "Anno Domini"] })


(deftest test-format-inst
  (is (= "03 3 03 3 AM 04 4 05 5 006 S Saturday Sat 07 7 02 2 J January Jan 01 1 2016 16 AD Anno Domini A"
         ((tongue/inst-formatter "HH H hh h a mm m ss s S eeeee eeee eee ee e dd d MMMMM MMMM MMM MM M yyyy yy GGG GGGG GGGGG" inst-strings)
           #inst "2016-01-02T03:04:05.006" UTC)))
  
  (testing "12hr time"
    (let [f (tongue/inst-formatter "hh:mm a" inst-strings)]
      (are [i s] (= (f i UTC) s)
        #inst "2016-01-01T00:00" "12:00 AM"
        #inst "2016-01-01T00:01" "12:01 AM"
        #inst "2016-01-01T01:00" "01:00 AM"
        #inst "2016-01-01T12:00" "12:00 PM"
        #inst "2016-01-01T12:01" "12:01 PM"
        #inst "2016-01-01T13:00" "01:00 PM"
        #inst "2016-01-01T23:59" "11:59 PM")))
  
  (testing "escaping"
    (are [f s] (= ((tongue/inst-formatter f inst-strings) #inst "2016-01-02T03:04:05.006" UTC) s)
      "hh:mm 'hh:mm' a" "03:04 hh:mm AM"
      "hh:mm '' a" "03:04  AM"
      "hh:mm 'h''m' a" "03:04 hm AM"))
  
  (testing "timezones"
    (are [i tz s] (= ((tongue/inst-formatter "yyyy-MM-dd HH:mm:ss" {}) i tz) s)
      #inst "2016-07-09T12:30:55" UTC   "2016-07-09 12:30:55"
      #inst "2016-07-09T12:30:55" GMT+6 "2016-07-09 18:30:55"
      #inst "2016-07-09T23:30:55" GMT+6 "2016-07-10 05:30:55")))


(deftest test-translate
  (let [dicts { :en { :inst-long  (tongue/inst-formatter "MMMM d, yyyy 'at' h:mm a" inst-strings)
                      :inst-subst "inst %1"
                      :tongue/format-inst (tongue/inst-formatter "M/d/yy h:mm a" inst-strings) }
                :ru { :inst-subst "момент %1" }}
        translate (tongue/build-translate dicts)]
    
    (are [l k a t] (= (apply translate l k a) t)
      :en :inst-long  [#inst "2016-07-09T01:00:00.000" UTC]   "July 9, 2016 at 1:00 AM" ;; UTC timezone
      :en :inst-long  [#inst "2016-07-09T01:00:00.000" GMT+6] "July 9, 2016 at 7:00 AM" ;; GMT+6 timezone
      ;; Expect TZ to be set to UTC in node.js/CLJS env
      :en :inst-long  [#inst "2016-07-09T01:00:00.000"] "July 9, 2016 at 1:00 AM"       ;; UTC assumed by default
      :en :inst-subst [#inst "2016-07-09T01:00:00.000"] "inst 7/9/16 1:00 AM"           ;; substitutions use :tongue/format-inst
      :ru :inst-subst [#inst "2016-07-09T01:00:00.000"] "момент 2016-07-09T01:00:00"))) ;; or default formatter

