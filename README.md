<img src="https://dl.dropboxusercontent.com/u/561580/imgs/tongue_logo.svg">

Tongue is a do-it-yourself i18n library for Clojure and ClojureScript.

Tongue is very simple yet capable:

- It comes with absolutely no built-in knowledge of world locales. It has all the tooling for you to define them yourself.
- There’s no special dictionary format, just plain Clojure maps.
- For complex cases (pluralization, special wording, complex dispatch rules, etc) use regular Clojure functions.
- Tongue can be used from Clojure and ClojureScript.

### Who’s using Rum?

- [Cognician](https://www.cognician.com), coaching platform


### Usage

Add to `project.clj`:

```clj
[tongue "0.1.0"]
```

Define dictionaries:

```clj
(require '[tongue.core :as tongue])

(def dicts
  { :en { ;; simple keys
          :color "Color"
          :flower "Flower"
          
          ;; namespaced keys
          :weather/rain   "Rain"
          :weather/clouds "Clouds"
          
          ;; nested maps will be unpacked into namespaced keys
          ;; this is purely for ease of writing dictionaries
          :animals { :dog "Dog"   ;; => :animals/dog
                     :cat "Cat" } ;; => :animals/cat
                     
          ;; substitutions
          :welcome "Hello, %1!"
          :between "Value must be between %1 and %2"
          
          ;; arbitrary functions
          :count (fn [x]
                   (cond
                     (zero? x) "No items"
                     (= 1 x)   "1 item"
                     :else     "%1 items")) ;; note how we returned string with substitution
          
          ;; arbitrary function for locale-aware formatting
          :tongue/format-number (fn [x] (str/replace (str x) "." ","))
          
          ;; or use `tongue.core/number-formatter` helper
          :tongue/format-number (tongue/number-formatter { :group ","
                                                           :decimal "." })
        }
                   
    :en-GB { :color "colour" } ;; sublang overrides
    :tongue/fallback :en }     ;; fallback locale key
```

Build translation function:

```clj
(def translate ;; [locale key & args] => string
  (tongue/build-translate dicts))
```

And go use it:

```clj
(translate :en :color) ;; => "Color"

;; namespaced keys
(translate :en :animals/dog) ;; => "Dog", taken from { :en { :animals { :dog "Dog }}}

;; substitutions
(translate :en :welcome "Nikita") ;; => "Welcome, Nikita!"
(translate :en :between 0 100) ;; => "Value must be between 0 and 100"

;; if key resolves to fn, it will be called with provided arguments
(translate :en :count 0) ;; => "No items"
(translate :en :count 1) ;; => "1 item"
(translate :en :count 2) ;; => "2 items"

;; if locale has :tongue/format-number key, substituted numbers will be formatted
(translate :en :count 9999.9) ;; => "9,999.9 items"

;; multi-tag locales will fall back to more generic versions 
;; :zh-Hans-CN will look in :zh-Hans-CN first, then :zh-Hans, then :zh, then fallback locale
(translate :en-GB :color) ;; => "Colour", taken from :en-GB
(translate :en-GB :flower) ;; => "Flower", taken from :en

;; if there’s no locale or no key in locale, fallback locale is used
(translate :ru :color) ;; => "Color", taken from :en as a fallback locale

;; if nothing can be found at all
(translate :en :unknown) ;; => "|Missing key :unknown|"
```

## Changes

### 0.1.0

Initial release

## License

Copyright © 2016 Nikita Prokopov

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
