(ns tongue.core
  (:require
    [clojure.string :as str]
    #?(:clj [clojure.future :refer [simple-keyword?]])
    [#?(:clj clojure.spec :cljs cljs.spec) :as spec]))


(spec/def ::decimal string?)
(spec/def ::group string?)


(defn number-formatter
  "Helper to build number format functions
   Accepts options map:
     { :decimal \".\"  ;; integer/fractional mark
       :group   \"\" } ;; thousands grouping mark
   Returns function (number => String)"
  [opts]
  (spec/assert (spec/keys :opt-un [::decimal ::group]) opts)
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
    (number? x) (let [formatter (or (lookup-template-for-locale dicts locale :tongue/format-number)
                                    str)]
                  (formatter x))
    :else       (str x)))


(spec/def ::locale simple-keyword?)
(spec/def ::key keyword?)


(defn- translate
  ([dicts locale key]
    (spec/assert ::locale locale)
    (spec/assert ::key key)
    (let [t (lookup-template dicts locale key)]
      (if (ifn? t) (t) t)))
  ([dicts locale key x]
    (spec/assert ::locale locale)
    (spec/assert ::key key)
    (let [t (lookup-template dicts locale key)
          s (if (ifn? t) (t x) t)]
      (str/replace s #"%1" (format-argument dicts locale x))))
  ([dicts locale key x & rest]
    (spec/assert ::locale locale)
    (spec/assert ::key key)
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


(spec/def ::template (spec/or :str string?
                              :fn ifn?))

(spec/def ::dict (spec/map-of keyword? (spec/or :plain  ::template
                                                :nested (spec/map-of keyword? ::template))))

(spec/def :tongue/fallback keyword?)
(spec/def ::dicts (spec/and
                    (spec/keys :opt [:tongue/fallback])
                    (spec/conformer #(dissoc % :tongue/fallback))
                    (spec/map-of keyword? ::dict)))


(spec/def ::translate
  (spec/fspec
    :args (spec/cat :locale keyword?
                    :key keyword?
                    :args (spec/* ::spec/any))
    :ret  string?))


(defn build-translate
  "Given dicts, builds translate function closed over these dicts:
   (build-translate dicts) => ( [locale key & args] => string )"
  [dicts]
  (spec/assert ::dicts dicts)
  (let [compiled-dicts (build-dicts dicts)]
    (fn
      ([locale key] (translate compiled-dicts locale key))
      ([locale key x] (translate compiled-dicts locale key x))
      ([locale key x & args]
        (apply translate compiled-dicts locale key x args)))))
