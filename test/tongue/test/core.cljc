(ns tongue.test.core
  (:require
    [tongue.core :as tongue]
    #?(:clj  [clojure.test :refer [deftest is are testing]]
       :cljs [cljs.test :as test :refer-macros [deftest is are testing]]))
  (:import #?(:clj [clojure.lang ExceptionInfo])))


#?(:cljs
   (def Throwable js/Error))


(deftest test-translate
  (let [dicts { :en-GB { :color  "colour"
                         :ns     { :a "a" }
                         :alias1 :ns/a
                         :alias2 :color
                         :alias3 { :a :alias1 }}

                :en    { :color     "color"
                         :common    "common"
                         :ns        { :a "A"
                                      :b "B"
                                      :c { :hoge { :foo "FOO" }
                                           :fuga { :bar "BAR" }}}
                         :ns/in-key "namespace in key"
                         :subst1    "one {1} argument"
                         :subst2    "two {1} {2} {1} arguments"
                         :subst3    "missing {1} {2} {3} argument"
                         :subst4    "{arg1} {arg2} map arguments"
                         :subst5    "{foo-bar} {baz-qux} kebab-cased map arguments"
                         :subst6    "{:foo} {foo/} {:foo/bar} {baz/foo-1} {{9foo#$!?%&=<>+_.'bar0/baz.foo-arg1}} namespaced map arguments"
                         :args      (fn [& args] (pr-str args))
                         :plural    (fn [x]
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
      ;; deeply nested
      :en    :ns.c.hoge/foo [] "FOO"
      :en    :ns.c.fuga/bar [] "BAR"
      ;; in the key
      :en    :ns/in-key [] "namespace in key"

      ;; arguments
      :en    :subst1  ["A"]     "one A argument"
      :en    :subst2  ["A" "B"] "two A B A arguments"
      :en    :subst3  ["A" "B"] "missing A B {Missing index 3} argument"
      :en    :subst4  [{:arg1 "A" :arg2 "B"}] "A B map arguments"
      :en    :subst5  [{:foo-bar "A" :baz-qux "B"}] "A B kebab-cased map arguments"
      :en    :subst6  [{:foo "A" :foo/bar "B" :baz/foo-1 "C" :9foo#$!?%&=<>+_.'bar0/baz.foo-arg1 "D"}] "{:foo} {foo/} {:foo/bar} C {D} namespaced map arguments"

      ;; fns
      :en    :args   ["A"]     "(\"A\")"
      :en    :args   ["A" "B"] "(\"A\" \"B\")"
      :en    :plural [0]       "no items"
      :en    :plural [1]       "1 item"
      :en    :plural [2]       "2 items"
      :ru    :plural [0]       "ничего"
      :ru    :plural [1]       "1 штука"
      :ru    :plural [5]       "5 штук"
      
      ;; aliases
      :en-GB :alias1   [] "a"
      :en-GB :alias2   [] "colour"
      :en-GB :alias3/a [] "a"))


  ;; should work without :tongue/fallback
  (let [t (tongue/build-translate { :en { :key "{1} value" }
                                    :ru { :key "{1} значение" } })]
    (is (= "1000.1 value" (t :en :key 1000.1)))
    (is (= "1000.1 значение" (t :ru :key 1000.1)))
    (is (= "{Missing key :key}" (t :de :key 1000.1)))))


(deftest test-invalid-alias
  (testing "Should throw if defining mutually recursive aliases"
    (try
      (tongue/build-translate { :en { :a :b :b :a } })
      (throw (ex-info "Recursive aliases should throw" {}))
      #?(:clj (catch ExceptionInfo e
                (is (= (-> e :data :keys)) #{:a :b}))
         :cljs (catch :default e
                 (is (= (-> e :data :keys)) #{:a :b}))))))


(deftest test-regex-escape
  (let [t (tongue/build-translate { :en { :key "Here's my {1}" } })]
    (is (= "Here's my $1.02 $&" (t :en :key "$1.02 $&"))))

  (let [t (tongue/build-translate { :en { :key "Here's my {1} {2} {3}" } })]
    (is (= "Here's my $1.3 $2.x $&" (t :en :key "$1.3" "$2.x" "$&")))))


(deftest test-errors
  (is (thrown-with-msg? Throwable #"Assert failed: :tongue/\.\.\. keys can only be specified at top level"
        (tongue/build-translate {:en {:ns {:tongue/format-number nil}}}))))

(deftest test-missing-key
  (let [t (tongue/build-translate {:en {}})]
    (is (= "{Missing key :unknown}" (t :en :unknown))))

  (let [t (tongue/build-translate {:en {:tongue/missing-key "Missing key {1}"}})]
    (is (= "Missing key :unknown" (t :en :unknown)))
    (is (= "Missing key :unknown" (t :en-US :unknown))))

  (let [t (tongue/build-translate {:en {:tongue/missing-key "Missing key {1}"}
                                   :ru {:tongue/missing-key "Не определен ключ {1}"}})]
    (is (= "Missing key :unknown" (t :en :unknown)))
    (is (= "Не определен ключ :unknown" (t :ru :unknown))))

  (let [t (tongue/build-translate {:en {:tongue/missing-key "Missing key {1}"}
                                   :tongue/fallback :en})]
    (is (= "Missing key :unknown" (t :ru :unknown))))

  (let [t (tongue/build-translate {:en {:tongue/missing-key nil}})]
    (is (= nil (t :en :unknown))))

  (let [t (tongue/build-translate {:en {:tongue/missing-key "Missing key {1}"}
                                   :ru {:tongue/missing-key nil}
                                   :tongue/fallback :en})]
    (is (= "Missing key :unknown" (t :en :unknown)))
    (is (= nil (t :ru :unknown)))
    (is (= nil (t :ru-RU :unknown)))))

(deftest test-functional-interpolation
  (let [translate (tongue/build-translate {:en-GB {:country "the UK"}
                                           :en-US {:country "the US"}
                                           :en {:welcome "Welcome to {country}!"}
                                           :ru {:welcome "Добро пожаловать в Россию!"}})
        message (fn message [locale k]
                  (translate locale k (partial message locale)))]
    (are [l t] (= (message l :welcome) t)
      :en-GB "Welcome to the UK!"
      :en-US "Welcome to the US!"
      :ru-RU "Добро пожаловать в Россию!")))

#_(clojure.test/test-vars [#'test-errors])
