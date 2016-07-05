(ns tongue.test
  (:require
    [tongue.core :as tongue]
    [#?(:clj clojure.test :cljs cljs.test) :as test :refer [deftest is are testing]]))


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
                         :tongue/format-number (tongue/number-formatter { :decimal "." :group "," }) }
                
                :ru    { :color  "цвет"
                         :plural (fn [x]
                                   (cond
                                     (zero? x) "ничего"
                                     (= 1 x)   "%1 штука"
                                     :else     "%1 штук"))
                         :num    "число %1"
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
      :de    :num   [1000.1]         "num 1,000.1"           ;; fallback
)))

;; (test/test-ns 'tongue.test)
