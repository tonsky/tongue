(ns tongue.number
  (:require
    [clojure.string :as str]
    [tongue.macro :as macro]
    #?(:clj [clojure.spec.alpha :as spec])))


(macro/with-spec
  (spec/def ::decimal string?)
  (spec/def ::group string?)
  (spec/def ::options (spec/keys :opt-un [::decimal ::group])))


(defn formatter
  "Helper to build number format functions
   Accepts options map:
     { :decimal \".\"  ;; integer/fractional mark
       :group   \"\" } ;; thousands grouping mark
   Returns function (number => String)"
  [opts]
  (macro/with-spec
    (spec/assert ::options opts))
  (let [{:keys [decimal group]
         :or   { decimal "."
                 group   "" }} opts]
    (cond
      (and (= "." decimal ) (= "" group))
      str

      (= "" group)
      #(str/replace (str %) "." decimal )

      :else
      (fn [x]
        (let [[_ sign integer-part fraction-part] (re-matches #"(-?)(\d+)\.?(\d*)" (str x))
              len (count integer-part)]
          (str sign
               (loop [idx (rem len 3)
                      res (subs integer-part 0 (rem len 3))]
                 (if (< idx len)
                   (recur (+ idx 3) (str res (when (pos? idx) group) (subs integer-part idx (+ idx 3))))
                   res))
               (when (not= "" fraction-part)
                 (str decimal  fraction-part))))))))
