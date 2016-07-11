<img src="https://dl.dropboxusercontent.com/u/561580/imgs/tongue_logo.svg">

Tongue is a do-it-yourself i18n library for Clojure and ClojureScript.

Tongue is very simple yet capable:

- It comes with no built-in knowledge of world locales. It has all the tooling for you to define locales yourself thought.
- Dictionaries are just Clojure maps.
- Translations are either strings, template strings or arbitrary functions.
- By relying on arbitrary functions, Tongue does not limit you in how to handle complex cases: pluralization, special wording, complex dispatch rules, etc.
- Tongue can be used from Clojure and ClojureScript.

### Who’s using Tongue?

- [Cognician](https://www.cognician.com), coaching platform

## Setup

Add to `project.clj`:

```clj
[tongue "0.1.1"]
```

In production:

-  Add `-Dclojure.spec.compile-asserts=false` to JVM options (actual JVM on Clojure, during build on ClojureScript)

In development:

- Add `-Dclojure.spec.check-asserts=true` to JVM options. 

## Usage

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
          ;; this is purely for ease of dictionary writing
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
                     :else     "%1 items")) ;; you can return string with substitutions
        }
                   
    :en-GB { :color "colour" } ;; sublang overrides
    :tongue/fallback :en }     ;; fallback locale key
```

Then build translation function:

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

;; multi-tag locales will fall back to more generic versions 
;; :zh-Hans-CN will look in :zh-Hans-CN first, then :zh-Hans, then :zh, then fallback locale
(translate :en-GB :color) ;; => "Colour", taken from :en-GB
(translate :en-GB :flower) ;; => "Flower", taken from :en

;; if there’s no locale or no key in locale, fallback locale is used
(translate :ru :color) ;; => "Color", taken from :en as a fallback locale

;; if nothing can be found at all
(translate :en :unknown) ;; => "|Missing key :unknown|"
```

### Localizing numbers

Tongue can help you build localized number formatters:

```clj
(def format-number-en ;; [number] => string
  (tongue/number-formatter { :group ","
                             :decimal "." }))
                             
(format-number-en 9999.9) ;; => "9,999.9"
```

Use it directly or add `:tongue/format-number` key to locale’s dictionary. That way format will be applied to all numeric substituions:

```clj
(def dicts
  { :en { :tongue/format-number format-number-en
          :count "%1 items" }
    :ru { :tongue/format-number (tongue/number-formatter { :group " "
                                                           :decimal "," })
          :count "%1 штук" }})

(def translate
  (tongue/build-translate dicts))

;; if locale has :tongue/format-number key, substituted numbers will be formatted
(translate :en :count 9999.9) ;; => "9,999.9 items"
(translate :ru :count 9999.9) ;; => "9 999,9 штук"

;; hint: if you only need a number, use :tongue/format-number key directly
(translate :en :tongue/format-number 9999.9) ;; => "9,999.9"
```

### Localizing dates

It works almost the same way as with numbers, but requires a little more setup.

First, you’ll need locale strings:

```clj
(def inst-strings-en
  { :weekdays-narrow ["S" "M" "T" "W" "T" "F" "S"]
    :weekdays-short  ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]
    :weekdays-long   ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"]
    :months-narrow   ["J" "F" "M" "A" "M" "J" "J" "A" "S" "O" "N" "D"]
    :months-short    ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]
    :months-long     ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October" "November" "December"]
    :day-periods     ["AM" "PM"]
    :eras-narrow     ["B" "A"]
    :eras-short      ["BC" "AD"]
    :eras-long       ["Before Christ" "Anno Domini"] })
```

Feel free to omit keys you’re not going to use. E.g. for ISO 8601 none of these strings are used at all.

Then build a datetime formatter:

```clj
(def format-inst ;; [inst] | [inst tz] => string
  (tongue/inst-formatter "MMM d, yyyy 'at' h:mm a" inst-strings-en))
```

And it’s ready to use:

```clj
(format-inst #inst "2016-07-11T22:31:00+06:00") ;; => "Jul 11, 2016 at 4:31 PM"

(format-inst
  #inst "2016-07-11T22:31:00+06:00"
  (java.util.TimeZone/getTimeZone "Asia/Novosibirsk")) ;; => "Jul 11, 2016 at 10:31 PM"
```

`tongue.core/inst-formatter` builds a function that has two arities: just instant or instant and timezone:

|  | Clojure | ClojureScript |
| --- | --- | --- |
| type of instant | `java.util.Date` | `js/Date` |
| type of timezone | `java.util.Timezone` | integer GMT offset in minutes, e.g. `360` for GMT+6 |
| if tz is omitted | assume UTC | assume browser timezone |

As with numbers, put a `:tongue/format-inst` key into dictionary to get default formatting for datetime substitutions:

```clj
(def dicts
  { :en { :tongue/format-inst (tongue/inst-formatter "MMM d, yyyy" inst-strings-en)
          :pub-ts "Published at %1" } })

(def translate
  (tongue/build-translate dicts))

;; if locale has :tongue/format-inst key, substituted instants will be formatted
(translate :en :put-ts #inst "2016-01-01") ;; => "Published at January 1, 2016"
```

Use multiple keys if you need several datetime format options:

```clj
(def dicts
  { :en { :inst-full  (tongue/inst-formatter "MMM d, yyyy 'at' h:mm a" inst-strings-en)
          :inst-short (tongue/inst-formatter "M/d h:mm a" inst-strings-en)
          :date-full  (tongue/inst-formatter "MMM d, yyyy" inst-strings-en)
          :date-short (tongue/inst-formatter "M/d/yy" inst-strings-en) }})

(def translate (tongue/build-translate dicts))

(translate :en :date-full #inst "2016-01-01") ;; => "January 1, 2016"
(translate :en :date-short #inst "2016-01-01") ;; => "1/1/16"

;; You can use timezones too
(def tz (java.util.TimeZone/getTimeZone "Asia/Novosibirsk"))
(translate :en :inst-short #inst "2016-01-01T12:00:00+06:00" tz) ;; => "January 1, 2016 at 12:00 PM"
```


Full list of formatting options:

| Code | Meaning |
| ---- | ------- |
| `HH` | Hour (00-23), padded |
| `H` | Hour (0-23) |
| `hh` | Hour (01-12), padded |
| `h` | Hour (1-12) |
| `a` | AM/PM from `:day-periods` |
| `mm` | Minutes (00-59), padded |
| `m` | Minutes (0-59) |
| `ss` | Seconds (00-59), padded |
| `s` | Seconds (0-59) |
| `S` | Milliseconds (000-999), padded |
| `eeeee` | `:weekdays-narrow` |
| `eeee` | `:weekdays-long` |
| `eee` | `:weekdays-short` |
| `ee` | Weekday (01-07, Sunday = 01), padded |
| `e` | Weekday (1-7, Sunday = 1) |
| `dd` | Day of month (01-31), padded |
| `d` | Day of month (1-31) |
| `MMMMM` | `:months-narrow` |
| `MMMM` | `:months-long` |
| `MMM` | `:months-short` |
| `MM` | Month (01-12, January = 01), padded |
| `M` | Month (1-12, January = 1) |
| `yyyy` | Full year (0-9999) |
| `yy` | Last two digits of a year (00-99) |
| `GGGGG` | `:eras-narrow` |
| `GGGG` | `:eras-long` |
| `GGG` | `:eras-short` |
| `'...'` | anything in single quotes is escaped |


## Changes

### 0.1.1

- Absense of format rules shouldn’t break `translate`
- number-format should not use fallback locale
- updated to clojure.spec 1.9.0-alpha9

### 0.1.0

Initial release

## License

Copyright © 2016 Nikita Prokopov

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
