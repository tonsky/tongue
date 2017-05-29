(ns tongue.test
  (:require
    [tongue.core :as tongue]
    [tongue.test.core]
    [tongue.test.inst]
    [tongue.test.number]
    #?(:clj  [clojure.java.shell])
    #?(:clj  [clojure.test :refer [deftest is are testing]]
       :cljs [cljs.test :as test :refer-macros [deftest is are testing]])))


#?(:cljs
(defn ^:export test_all []
  (enable-console-print!)
  (let [results (volatile! nil)]
    (defmethod test/report [:cljs.test/default :end-run-tests] [m]
      (vreset! results (dissoc m :type)))
    (test/run-all-tests #"tongue\.test\..*")
    (clj->js @results))))


#?(:clj
(defn test-clojure [& args]
  (println "\n--- Testing on Clojure" (clojure-version) "---")
  (clojure.test/run-all-tests #"tongue\..*")))


#?(:clj
(defn test-node [& args]
  (println "\n--- Testing on ClojureScript ---")
  (let [res (apply clojure.java.shell/sh "node" "test/tongue/test.js" args)]
    (println (:out res))
    (binding [*out* *err*]
      (println (:err res)))
    (System/exit (:exit res)))))
