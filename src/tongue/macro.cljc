(ns tongue.macro
  #?(:cljs (:require-macros tongue.macro)))

#?(:clj
(defmacro with-spec [& body]
  (let [cljs? (boolean (:ns &env))]
    (when-not cljs?
      `(do ~@body)))))

#?(:clj
(defmacro if-some-reduced [[name expr] then else]
  `(if-some [~name ~expr]
     (let [~name (unreduced ~name)]
       ~then)
     ~else)))