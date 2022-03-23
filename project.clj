(defproject tongue "0.4.4"
  :description  "DIY i18n library for Clojure/Script"
  :url          "https://github.com/tonsky/tongue"
  :license      {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure       "1.10.0"   :scope "provided"]
    [org.clojure/clojurescript "1.10.439" :scope "provided"]
    [clojure-future-spec       "1.9.0"]
  ]
  
  :global-vars { *warn-on-reflection* true }
  
  :plugins [[lein-cljsbuild "1.1.7"]]
  
  :profiles {
    :dev {
      :jvm-opts ["-Dclojure.spec.check-asserts=true"]
    }
    :1.9 {
      :dependencies [
        [org.clojure/clojure       "1.9.0"]
        [org.clojure/clojurescript "1.9.946" :scope "provided"]
      ]  
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

  :deploy-repositories {
    "clojars" { :url "https://clojars.org/repo"
                :username "tonsky"
                :password :env/clojars_password
                :sign-releases false }
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
  
