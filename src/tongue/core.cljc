(ns tongue.core
  (:require
    [clojure.string :as str]))


(defn number-formatter
  "Helper to build number format functions
   Accepts options map:
     { :decimal \".\"  ;; integer/fractional mark
       :group   \"\" } ;; thousands grouping mark
   Returns function (number => String)"
  [opts]
  (let [{:keys [decimal  group]
         :or { decimal  "."
               group "" }} opts]
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


(defn- parse-long [s]
  #?(:cljs (js/parseInt s)
     :clj  (Long/parseLong s)))


(defonce ^:private tags-cache (volatile! {}))


(defn- tags
  ":az-Arab-IR => (:az-Arab-IR :az-Arab :az), cached"
  [locale]
  (or (@tags-cache locale)
      (let [tags (loop [subtags  (str/split (name locale) #"-")
                        last-tag nil
                        tags     ()]
                   (if-some [subtag (first subtags)]
                     (let [tag (str last-tag (when last-tag "-") subtag)]
                       (recur (next subtags) tag (conj tags (keyword tag))))
                     tags))]
        (vswap! tags-cache assoc locale tags)
        tags)))


(defn- lookup-template-for-locale [dicts locale key]
  (when locale
    (loop [tags (tags locale)]
      (when-some [tag (first tags)]
        (or (get (get dicts tag) key)
            (recur (next tags)))))))


(defn- lookup-template [dicts locale key]
  (or (lookup-template-for-locale dicts locale key)
      (lookup-template-for-locale dicts (:tongue/fallback dicts) key)
      (str "|Missing key " key "|")))


(defn- format-argument [dicts locale x]
  (cond
    (number? x) ((or (lookup-template dicts locale :tongue/format-number) str) x)
    :else       (str x)))


(defn- translate
  ([dicts locale key]
    (let [t (lookup-template dicts locale key)]
      (if (ifn? t) (t) t)))
  ([dicts locale key x]
    (let [t (lookup-template dicts locale key)
          s (if (ifn? t) (t x) t)]
      (str/replace s #"%1" (format-argument dicts locale x))))
  ([dicts locale key x & rest]
    (let [args (cons x rest)
          t    (lookup-template dicts locale key)
          s    (if (ifn? t) (apply t x rest) t)]
      (str/replace s #"%(\d+)" (fn [[_ n]]
                                 (let [idx (dec (parse-long n))
                                       arg (nth args idx)]
                                   (format-argument dicts locale arg)))))))


(defn- build-dict
  "Collapses nested maps into namespaced keywords:
   { :ns { :key 1 }} => { :ns/key 1 }"
  [dict]
  (reduce-kv
    (fn [dict k v]
      (if (map? v)
        (reduce-kv (fn [dict k' v']
                     (assoc dict (keyword (name k) (name k')) v')) dict v)
        (assoc dict k v)))
    {} dict))


(defn- build-dicts
  [lang->dict]
  (reduce-kv (fn [acc lang dict]
               (assoc acc lang (if (map? dict) (build-dict dict) dict)))
             {} lang->dict))


(defn build-translate
  "Given dicts, builds translate function closed over these dicts:
   (build-translate dicts) => ( [locale key & args] => string )"
  [lang->dict]
  (partial translate (build-dicts lang->dict)))


