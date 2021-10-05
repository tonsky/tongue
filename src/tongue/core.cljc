(ns tongue.core
  (:require
    [clojure.string :as str]
    [tongue.inst :as inst]
    [tongue.number :as number]
    [tongue.macro :as macro]
    #?(:clj [clojure.future :refer :all])
    #?(:clj [clojure.spec.alpha :as spec])))


(def inst-formatter inst/formatter)


(def format-inst-iso (inst-formatter "{year}-{month-numeric-padded}-{day-padded}T{hour24-padded}:{minutes-padded}:{seconds-padded}" {}))


(def number-formatter number/formatter)


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
      (str "{Missing key " key "}")))


(defn- escape-re-subst [str]
  #?(:clj (java.util.regex.Matcher/quoteReplacement str)
     :cljs (str/replace str #"\$" "$$$$")))


(defn- format-argument [dicts locale x]
  (cond
    (number? x) (let [formatter (or (lookup-template-for-locale dicts locale :tongue/format-number)
                                    str)]
                  (formatter x))
    (inst? x)   (let [formatter (or (lookup-template-for-locale dicts locale :tongue/format-inst)
                                    format-inst-iso)]
                  (formatter x))
    :else       (str x)))


(defprotocol IInterpolate
  (interpolate-named [v dicts locale interpolations]
    "Interpolate the value `v` with named `interpolations` in the provided map.")

  (interpolate-positional [v dicts locale interpolations]
    "Interpolate the value `v` with positional `interpolations` in the provided vector."))


(extend-type #?(:clj String
                :cljs js/String)
  IInterpolate
  (interpolate-named [s dicts locale interpolations]
    (str/replace s #?(:clj  #"\{([\w*!_?$%&=<>'\-+.#0-9]+|[\w*!_?$%&=<>'\-+.#0-9]+\/[\w*!_?$%&=<>'\-+.#0-9:]+)\}"
                      :cljs #"\{([\w*!_?$%&=<>'\-+.#0-9]+|[\w*!_?$%&=<>'\-+.#0-9]+/[\w*!_?$%&=<>'\-+.#0-9:]+)\}")
                 (fn [[_ k]]
                   (format-argument dicts locale (get interpolations (keyword k))))))

  (interpolate-positional [s dicts locale interpolations]
    (str/replace s #"\{(\d+)\}"
                 (fn [[_ n]]
                   (let [idx (parse-long n)
                         arg (nth interpolations (dec idx)
                                  (str "{Missing index " idx "}"))]
                     (format-argument dicts locale arg))))))


(macro/with-spec
  (spec/def ::locale simple-keyword?)
  (spec/def ::key keyword?))


(defn- translate
  ([dicts locale key]
    (macro/with-spec
      (spec/assert ::locale locale)
      (spec/assert ::key key))
    (let [t (lookup-template dicts locale key)]
      (if (ifn? t) (t) t)))
  ([dicts locale key x]
    (macro/with-spec
      (spec/assert ::locale locale)
      (spec/assert ::key key))
    (let [t (lookup-template dicts locale key)
          v (if (ifn? t) (t x) t)]
      (if (map? x)
        (interpolate-named v dicts locale x)
        (interpolate-positional v dicts locale [x]))))
  ([dicts locale key x & rest]
    (macro/with-spec
      (spec/assert ::locale locale)
      (spec/assert ::key key))
    (let [args (cons x rest)
          t (lookup-template dicts locale key)]
      (interpolate-positional (if (ifn? t) (apply t x rest) t) dicts locale args))))


(defn- append-ns [ns segment]
  (str (when ns (str ns "."))
       segment))


(defn- build-dict
  "Collapses nested maps into namespaced keywords:
   { :ns { :key 1 }} => { :ns/key 1 }
   { :animal { :flying { :bird 420 }}} => { :animal.flying/bird 420 }"
  ([dict] (build-dict nil dict))
  ([ns dict]
    (reduce-kv
      (fn [aggr key value]
        (cond
          (= "tongue" (namespace key))
          (do
            (assert (nil? ns) ":tongue/... keys can only be specified at top level")
            (assoc aggr key value))

          (map? value)
          (merge aggr (build-dict (append-ns ns (name key)) value))

          :else
          (assoc aggr (keyword (or ns (namespace key)) (name key)) value)))
      {} dict)))


(defn- resolve-alias-1
  [m k]
  (loop [v k
         path #{}]
    (when (path v)
      (throw (ex-info "Unable to resolve mutually recursive alias" {:keys path})))
    (let [val (or (m v) v)]
      (if (= val v)
        val
        (recur val (conj path v))))))


(defn- resolve-aliases
  "Shallowly walks a map, and finds every value that is also a key in the same
  map, and replaces the value with the referenced value. Recursively walks the
  map to resolve layered aliases.

  (resolve-aliases {:a 1 :b 2 :c :a}) ;;=> {:a 1 :b 2 :c 1}"
  [m]
  (into {} (map #(vector (first %) (resolve-alias-1 m (second %))) m)))


(defn build-dicts [dicts]
  (reduce-kv
    (fn [acc lang dict]
      (assoc acc lang (if (map? dict) (-> dict build-dict resolve-aliases) dict)))
    {} dicts))


(macro/with-spec
  (spec/def ::template (spec/or :str string?
                                :fn ifn?))

  (spec/def :tongue/format-number ifn?)
  (spec/def :tongue/format-inst ifn?)

  (spec/def ::dict (spec/and
                     (spec/keys :opt [:tongue/format-number :tongue/format-inst])
                     (spec/map-of keyword? (spec/or :plain  ::template
                                                    :nested (spec/map-of keyword? ::template)))))

  (spec/def :tongue/fallback keyword?)
  (spec/def ::dicts (spec/and
                      (spec/keys :opt [:tongue/fallback])
                      (spec/conformer #(dissoc % :tongue/fallback))
                      (spec/map-of keyword? ::dict))))


(defn build-translate
  "Given dicts, builds translate function closed over these dicts:

       (build-translate dicts) => ( [locale key & args] => string )"
  [dicts]
  (macro/with-spec
    (spec/assert ::dicts dicts))
  (let [compiled-dicts (build-dicts dicts)]
    (fn
      ([locale key]   (translate compiled-dicts locale key))
      ([locale key x] (translate compiled-dicts locale key x))
      ([locale key x & args]
        (apply translate compiled-dicts locale key x args)))))
