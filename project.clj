(defproject tongue "0.2.2"
  :description  "DIY i18n library for Clojure/Script"
  :url          "https://github.com/tonsky/tongue"
  :license      {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
    [org.clojure/clojurescript "1.9.562" :scope "provided"]
    [clojure-future-spec "1.9.0-alpha17"]
  ]
  
  :global-vars { *warn-on-reflection* true }
  
  :plugins [[lein-cljsbuild "1.1.6"]]
  
  :profiles {
    :dev {
      :jvm-opts ["-Dclojure.spec.check-asserts=true"]
    }
    :1.8 {
      :dependencies [
        [org.clojure/clojure "1.8.0"]
      ]  
    }
  }

  :aliases {
    "build-all" ["cljsbuild" "once" "test"]
    "test-clj"  ["do"
                  ["run" "-m" "tongue.test/test-clojure"]
                  ["with-profile" "dev,1.8" "run" "-m" "tongue.test/test-clojure"]]
    "test-cljs" ["run" "-m" "tongue.test/test-node"]
    "test-all"  ["do"
                  ["clean"]
                  ["build-all"]
                  ["test-clj"]
                  ["test-cljs"]]
  }

  :repository-auth {
    #"clojars" { :username "tonsky" :password :env/clojars_password }
  }
  
  :cljsbuild
  { :builds
    [{ :id "test"
       :source-paths ["src" "test"]
       :compiler
       { :main           tongue.test
         :output-to      "target/test.js"
         :optimizations  :advanced
         :parallel-build true }}]})
  
