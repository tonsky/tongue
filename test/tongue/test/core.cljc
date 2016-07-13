(ns tongue.test.core
  (:require
    [tongue.core :as tongue]
    #?(:clj  [clojure.test :refer [deftest is are testing]]
       :cljs [cljs.test :as test :refer-macros [deftest is are testing]])))


(deftest test-translate
  (let [dicts { :en-GB { :color  "colour"
                         :ns     { :a "a" }}
                
                :en    { :color  "color"
                         :common "common"
                         :ns     { :a "A" :b "B" }
                         :subst1 "one {1} argument"
                         :subst2 "two {1} {2} {1} arguments"
                         :args   (fn [& args] (pr-str args))
                         :plural (fn [x]
                                   (cond
                                     (zero? x) "no items"
                                     (= 1 x)   "{1} item"
                                     :else     "{1} items")) }
                
                :ru    { :color  "цвет"
                         :plural (fn [x]
                                   (cond
                                     (zero? x) "ничего"
                                     (= 1 x)   "{1} штука"
                                     :else     "{1} штук")) }
                
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
      :en-GB     :unknw  [] "{Missing key :unknw}" ;; missing key
         
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
      :ru    :plural [5]       "5 штук"))
  
  
  ;; should work without :tongue/fallback
  (let [t (tongue/build-translate { :en { :key "{1} value" }
                                    :ru { :key "{1} значение" } })]
    (is (= "1000.1 value" (t :en :key 1000.1))) 
    (is (= "1000.1 значение" (t :ru :key 1000.1)))
    (is (= "{Missing key :key}" (t :de :key 1000.1)))))
