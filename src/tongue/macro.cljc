(ns tongue.macro
  #?(:cljs (:require-macros tongue.macro)))

#?(:clj
(defmacro with-spec [& body]
  (let [cljs? (boolean (:ns &env))]
    (when-not cljs?
      `(do ~@body)))))
