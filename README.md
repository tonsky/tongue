<img src="http://s.tonsky.me/imgs/tongue_logo.svg">

[![tongue](https://img.shields.io/clojars/v/tongue.svg)](http://clojars.org/tongue) [![build status](https://img.shields.io/circleci/project/tonsky/tongue.svg)](https://circleci.com/gh/tonsky/tongue) [![docs on cljdoc](https://cljdoc.xyz/badge/tongue)](https://cljdoc.xyz/d/tongue/tongue/CURRENT)

Tongue is a do-it-yourself i18n library for Clojure and ClojureScript.

Tongue is very simple yet capable:

- Dictionaries are just Clojure maps.
- Translations are either strings, template strings or arbitrary functions.
- No additional build steps, no runtime resource loading.
- It comes with no built-in knowledge of world locales. It has all the tooling for you to define locales yourself though.
- Pure Clojure implementation, no dependencies.
- Can be used from both Clojure and ClojureScript.

In contrast with other i18n solutions relying on complex and limiting string-based syntax for defining pluralization, wording, special cases etc, Tongue lets you use arbitrary functions. It gives you convenience, code reuse and endless possibilities.

As a result you have a library that handles exactly your case well with as much detail and precision as you need.

### Who’s using Tongue?

- [Cognician](https://www.cognician.com), coaching platform

## Setup

Add to `project.clj`:

```clj
[tongue "0.2.7"]
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
          :welcome "Hello, {1}!"
          :between "Value must be between {1} and {2}"
          ;; For using a map
          :mail-title "{user}, {title} - Message received."

          ;; arbitrary functions
          :count (fn [x]
                   (cond
                     (zero? x) "No items"
                     (= 1 x)   "1 item"
                     :else     "{1} items")) ;; you can return string with substitutions
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
(translate :en :welcome "Nikita") ;; => "Hello, Nikita!"
(translate :en :between 0 100) ;; => "Value must be between 0 and 100"
(translate :en :mail-title {:user "Tom" :title "New message"}) ;; => "Tom, New message - Message received."

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

Use it directly or add `:tongue/format-number` key to locale’s dictionary. That way format will be applied to all numeric substitutions:

```clj
(def dicts
  { :en { :tongue/format-number format-number-en
          :count "{1} items" }
    :ru { :tongue/format-number (tongue/number-formatter { :group " "
                                                           :decimal "," })
          :count "{1} штук" }})

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
    :dayperiods      ["AM" "PM"]
    :eras-short      ["BC" "AD"]
    :eras-long       ["Before Christ" "Anno Domini"] })
```

Feel free to omit keys you’re not going to use. E.g. for ISO 8601 none of these strings are used at all.

Then build a datetime formatter:

```clj
(def format-inst ;; [inst] | [inst tz] => string
  (tongue/inst-formatter "{month-short} {day}, {year} at {hour12}:{minutes-padded} {dayperiod}" inst-strings-en))
```

And it’s ready to use:

```clj
(format-inst #inst "2016-07-11T22:31:00+06:00") ;; => "Jul 11, 2016 at 4:31 PM"

(format-inst
  #inst "2016-07-11T22:31:00+06:00"
  (java.util.TimeZone/getTimeZone "Asia/Novosibirsk")) ;; => "Jul 11, 2016 at 10:31 PM"
```

`tongue.core/inst-formatter` builds a function that has two arities: just instant or instant and timezone:

|                  | Clojure              | ClojureScript |
| ---------------- | -------------------- | --------- |
| instant: `clojure.core/Inst` protocol implementations | `java.util.Date`, `java.time.Instant`, ...     | `js/Date`, ... |
| timezone         | `java.util.Timezone` | integer GMT offset in minutes, e.g. `360` for GMT+6 |
| if tz is omitted | assume UTC           | assume browser timezone |

As with numbers, put a `:tongue/format-inst` key into dictionary to get default formatting for datetime substitutions:

```clj
(def dicts
  { :en { :tongue/format-inst (tongue/inst-formatter "{month-short} {day}, {year}" inst-strings-en)
          :published "Published at {1}" } })

(def translate
  (tongue/build-translate dicts))

;; if locale has :tongue/format-inst key, substituted instants will be formatted using it
(translate :en :published #inst "2016-01-01") ;; => "Published at January 1, 2016"
```

Use multiple keys if you need several datetime format options:

```clj
(def dicts
  { :en
    { :date-full     (tongue/inst-formatter "{month-long} {day}, {year}" inst-strings-en)
      :date-short    (tongue/inst-formatter "{month-numeric}/{day}/{year-2digit}" inst-strings-en)
      :time-military (tongue/inst-formatter "{hour24-padded}{minutes-padded}")}})

(def translate (tongue/build-translate dicts))

(translate :en :date-full     #inst "2016-01-01T15:00:00") ;; => "January 1, 2016"
(translate :en :date-short    #inst "2016-01-01T15:00:00") ;; => "1/1/16"
(translate :en :time-military #inst "2016-01-01T15:00:00") ;; => "1500"

;; You can use timezones too
(def tz (java.util.TimeZone/getTimeZone "Asia/Novosibirsk"))  ;; GMT+6
(translate :en :time-military #inst "2016-01-01T15:00:00" tz) ;; => "2100"
```


Full list of formatting options:

| Code                 | Example        | Meaning              |
| -------------------- | -------------- | -------------------- |
| `{hour24-padded}`    | 00, 09, 12, 23 | Hour of day (00-23), 0-padded |
| `{hour24}`           | 0, 9, 12, 23   | Hour of day (0-23) |
| `{hour12-padded}`    | 12, 09, 12, 11 | Hour of day (01-12), 0-padded |
| `{hour12}`           | 12, 9, 12, 11  | Hour of day (1-12) |
| `{dayperiod}`        | AM, PM         | AM/PM from `:dayperiods` |
| `{minutes-padded}`   | 00, 30, 59     | Minutes (00-59), 0-padded |
| `{minutes}`          | 0, 30, 59      | Minutes (0-59) |
| `{seconds-padded}`   | 0, 30, 59      | Seconds (00-60), 0-padded |
| `{seconds}`          | 00, 30, 59     | Seconds (0-60) |
| `{milliseconds}`     | 000, 123, 999  | Milliseconds (000-999), always 0-padded |
| `{weekday-long}`     | Wednesday      | Weekday from `:weekdays-long` |
| `{weekday-short}`    | Wed, Thu       | Weekday from `:weekdays-short` |
| `{weekday-narrow}`   | W, T           | Weekday from `:weekdays-narrow` |
| `{weekday-numeric}`  | 1, 4, 5, 7     | Weekday number (1-7, Sunday = 1) |
| `{day-padded}`       | 01, 15, 29     | Day of month (01-31), 0-padded |
| `{day}`              | 1, 15, 29      | Day of month (1-31) |
| `{month-long}`       | January        | Month from `:months-long` |
| `{month-short}`      | Jan, Feb       | Month from `:months-short` |
| `{month-narrow}`     | J, F           | Month from `:months-narrow` |
| `{month-numeric-padded}` | 01, 02, 12 | Month number (01-12, January = 01), 0-padded |
| `{month-numeric}`    | 1, 2, 12       | Month number (1-12, January = 1) |
| `{year}`             | 1999, 2016     | Full year (0-9999) |
| `{year-2digit}`      | 99, 16         | Last two digits of a year (00-99) |
| `{era-long}`         | Anno Domini    | Era from `:eras-long` |
| `{era-short}`        | BC, AD         | Era from `:eras-short` |
| `...`                | ...            | anything not in `{}` is printed as-is |


## Changes

### 0.2.7 July 26, 2019

- Substitute placeholders from a map (PR #22, thx @katsuyasu-murata)

### 0.2.6

- Fix namespaced keys (PR #20, thx @JoelSanchez)

### 0.2.5

- Enable deep nesting of dicts (PR #18, thx @valerauko)
- Bumped `clojure-future-spec` to 1.9.0

### 0.2.4

- Don’t throw on missing argument index (#13)

### 0.2.3

- `[clojure-future-spec "1.9.0-beta4"]`

### 0.2.2

- Fixed incorrect substitution if replacement contained `$` (PR #7, thx [Christian Johansen](https://github.com/cjohansen))

### 0.2.1

- `[clojure-future-spec "1.9.0-alpha17"]`
- Tongue now works in both 1.8 and 1.9+ Clojure environments

### 0.2.0

- Removed clojure-future-spec, requires Clojure 1.9 or later

### 0.1.4

- Use unified `{}` syntax instead of `<...>`/`%x`

### 0.1.3

- Date/time formatting can accept arbitrary `Inst` protocol implementations

### 0.1.2

- Date/time formatting
- ClojureScript now runs tests too
- clojure.spec 1.9.0-alpha10
- Disabled spec for ClojureScript

### 0.1.1

- Absense of format rules shouldn’t break `translate`
- number-format should not use fallback locale
- updated to clojure.spec 1.9.0-alpha9

### 0.1.0

Initial release

## License

Copyright © 2016 Nikita Prokopov

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
