(ns tongue.test
  (:require
    [tongue.core :as tongue]
    [#?(:clj clojure.test :cljs cljs.test) :as test :refer [deftest is are testing]]
    [#?(:clj clojure.spec.test :cljs cljs.spec.test) :as spec.test]))


(deftest test-format-number
  (let [format-fn (tongue/number-formatter {:decimal "," :group " "})]
    (are [n s] (= (format-fn n ) s)
      0.1       "0,1"
      0.01      "0,01"
      0.09      "0,09"
      0         "0"
      500       "500"
      1500      "1 500"
      10500     "10 500"
      100500    "100 500"
      100500.1  "100 500,1"
      -1        "-1"
      -0.1      "-0,1"
      -500      "-500"
      -1500     "-1 500"
      -100500   "-100 500"
      -100500.1 "-100 500,1")))


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
  (let [dicts { :en-GB { :color  "colour"
                         :ns     { :a "a" }}
                
                :en    { :color  "color"
                         :common "common"
                         :ns     { :a "A" :b "B" }
                         :subst1 "one %1 argument"
                         :subst2 "two %1 %2 %1 arguments"
                         :args   (fn [& args] (pr-str args))
                         :plural (fn [x]
                                   (cond
                                     (zero? x) "no items"
                                     (= 1 x)   "%1 item"
                                     :else     "%1 items"))
                         :num    "num %1"
                         :nums   "nums %1 %2"
                         :inst-long  (tongue/inst-formatter "MMMM d, yyyy 'at' h:mm a" inst-strings)
                         :inst-subst "inst %1"
                         :tongue/format-number (tongue/number-formatter { :decimal "." :group "," })
                         :tongue/format-inst (tongue/inst-formatter "M/d/yy h:mm a" inst-strings) }
                
                :ru    { :color  "цвет"
                         :plural (fn [x]
                                   (cond
                                     (zero? x) "ничего"
                                     (= 1 x)   "%1 штука"
                                     :else     "%1 штук"))
                         :num "число %1"
                         :inst-subst "момент %1"
                         :tongue/format-number (tongue/number-formatter { :decimal "," :group " " }) }
                
                :tongue/fallback :en-US }
        translate (tongue/build-translate dicts)]
    
    (are [l k a t] (= (apply translate l k a) t)
      :en-GB :color  [] "colour"
      :en    :color  [] "color"   
      :ru    :color  [] "цвет"
         
      ;; fallbacks
      :en-GB     :common [] "common"               ;; :en-GB => :en
      :en-GB-inf :color  [] "colour"               ;; :en-GB-inf => :en-GB
      :en-GB-inf :common [] "common"               ;; :en-GB-inf => :en-GB => :en
      :de        :color  [] "color"                ;; :de => :tongue/fallback => :en-US => :en
      :en-GB     :unknw  [] "|Missing key :unknw|" ;; missing key
         
      ;; nested
      :en-GB :ns/a   [] "a"
      :en-GB :ns/b   [] "B"
      :en    :ns/a   [] "A"
      :en    :ns/b   [] "B"
         
      ;; arguments
      :en    :subst1  ["A"]     "one A argument"
      :en    :subst2  ["A" "B"] "two A B A arguments"
         
      ;; fns
      :en    :args   ["A"]     "(\"A\")"
      :en    :args   ["A" "B"] "(\"A\" \"B\")"
      :en    :plural [0]       "no items"
      :en    :plural [1]       "1 item"
      :en    :plural [2]       "2 items"
      :ru    :plural [0]       "ничего"
      :ru    :plural [1]       "1 штука"
      :ru    :plural [5]       "5 штук"
      
      ;; fns + locale-aware number format 
      :en    :plural [1000]    "1,000 items"
      :ru    :plural [1000]    "1 000 штук"
      
      ;; formatting numbers
      :en    :num   [1000.1]         "num 1,000.1"
      :en    :nums  [1000.1 -2000.2] "nums 1,000.1 -2,000.2"
      :en-GB :num   [1000.1]         "num 1,000.1"           ;; fallback to :en
      :ru    :num   [1000.1]         "число 1 000,1"
      :ru    :nums  [1000.1 -2000.2] "nums 1 000,1 -2 000,2" ;; :ru for numbers, fallback for pattern
      :en    :tongue/format-number [1000.1] "1,000.1" ;; using :tongue/format-number directly
         
      ;; formatting dates
      :en    :inst-long  [#inst "2016-07-09T01:00:00.000"]       "July 9, 2016 at 1:00 AM"
      :en    :inst-long  [#inst "2016-07-09T01:00:00.000" GMT+6] "July 9, 2016 at 7:00 AM"    ;; GMT+6 timezone
      :en    :inst-subst [#inst "2016-07-09T01:00:00.000"]       "inst 7/9/16 1:00 AM"        ;; substitutions use :tongue/format-inst
      :ru    :inst-subst [#inst "2016-07-09T01:00:00.000"]       "момент 2016-07-09T01:00:00" ;; or default formatter
    ))
  
  (let [t (tongue/build-translate { :en { :key "%1 value" }
                                    :ru { :key "%1 значение"
                                          :tongue/format-number (tongue/number-formatter { :decimal "," :group " " })}
                                    :tongue/fallback :ru })]
    ;; number format shouldn’t look into fallback
    (is (= "1000.1 value" (t :en :key 1000.1))) 
    
    ;; number format should follow tag generality chain :ru-inf => :ru
    (is (= "1 000,1 значение" (t :ru-inf :key 1000.1))))
  
  ;; should work without :tongue/fallback
  (let [t (tongue/build-translate { :en { :key "%1 value" }
                                    :ru { :key "%1 значение" } })]
    (is (= "1000.1 value" (t :en :key 1000.1))) 
    (is (= "1000.1 значение" (t :ru :key 1000.1)))
    (is (= "|Missing key :key|" (t :de :key 1000.1))))
)

;; (test/test-ns 'tongue.test)

