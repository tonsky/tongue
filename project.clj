(defproject tongue "0.2.0"
  :description  "DIY i18n library for Clojure/Script"
  :url          "https://github.com/tonsky/tongue"
  :license      {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.9.0-alpha10" :scope "provided"]
    [org.clojure/clojurescript "1.9.89" :scope "provided"]
  ]
  
  :global-vars { *warn-on-reflection* true }
  
  :plugins [[lein-cljsbuild "1.1.3"]]
  
  :profiles {
    :dev {
      :jvm-opts ["-Dclojure.spec.check-asserts=true"]
  }}
  
  :cljsbuild
  { :builds
    [{ :id "test"
       :source-paths ["src" "test"]
       :compiler
       { :main           tongue.test
         :output-to      "target/test.js"
         :optimizations  :advanced
         :parallel-build true }}]})
  
