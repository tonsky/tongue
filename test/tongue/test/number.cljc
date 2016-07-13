(ns tongue.test.number
  (:require
    [tongue.core :as tongue]
    #?(:clj  [clojure.test :refer [deftest is are testing]]
       :cljs [cljs.test :as test :refer-macros [deftest is are testing]])))


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
  (let [dicts { :en { :num    "num {1}"
                      :nums   "nums {1} {2}"
                      :plural (fn [x]
                                (cond
                                  (zero? x) "no items"
                                  (= 1 x)   "{1} item"
                                  :else     "{1} items"))
                      :tongue/format-number (tongue/number-formatter { :decimal "." :group "," }) }
                :ru { :num "число {1}"
                      :plural (fn [x]
                                (cond
                                  (zero? x) "ничего"
                                  (= 1 x)   "{1} штука"
                                  :else     "{1} штук"))
                      :tongue/format-number (tongue/number-formatter { :decimal "," :group " " }) }
                :tongue/fallback :en }
        translate (tongue/build-translate dicts)]

    (are [l k a t] (= (apply translate l k a) t)
      :en    :num   [1000.1]         "num 1,000.1"
      :en    :nums  [1000.1 -2000.2] "nums 1,000.1 -2,000.2"
      :en-GB :num   [1000.1]         "num 1,000.1"           ;; fallback to :en
      :ru    :num   [1000.1]         "число 1 000,1"
      :ru    :nums  [1000.1 -2000.2] "nums 1 000,1 -2 000,2" ;; :ru for numbers, fallback for pattern
      :en    :tongue/format-number [1000.1] "1,000.1" ;; using :tongue/format-number directly
         
      ;; fns + locale-aware number format 
      :en    :plural [1000]    "1,000 items"
      :ru    :plural [1000]    "1 000 штук"))

  
  (let [t (tongue/build-translate { :en { :key "{1} value" }
                                    :ru { :key "{1} значение"
                                          :tongue/format-number (tongue/number-formatter { :decimal "," :group " " })}
                                    :tongue/fallback :ru })]
    ;; number format shouldn’t look into fallback
    (is (= "1000.1 value" (t :en :key 1000.1))) 
    
    ;; number format should follow tag generality chain :ru-inf => :ru
    (is (= "1 000,1 значение" (t :ru-inf :key 1000.1)))))
