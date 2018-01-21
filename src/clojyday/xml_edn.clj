;; Copyright and license information at end of file

(ns clojyday.xml-edn
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [clojure.xml :as xml]
   [clojyday.core :as clojyday]))


(s/def ::day (s/int-in 1 32))

(s/def ::month #{:january
                 :february
                 :march
                 :april
                 :may
                 :june
                 :july
                 :august
                 :september
                 :october
                 :november
                 :december})

(s/def ::weekday #{:monday :tuesday :wednesday :thursday :friday :saturday :sunday})

(s/def ::which #{:first :second :third :fourth :last})

(s/def ::when #{:before :after})

(s/def ::valid-from int?)

(s/def ::valid-to int?)

(s/def ::every #{:every-year :2-years :4-years :5-years :6-years :odd-years :even-years})

(s/def ::description-key keyword?)

(s/def ::localized-type #{:official-holiday :unofficial-holiday})

(s/def ::substitute ::weekday)

(s/def ::moving-conditions
  (s/coll-of
   (s/keys :req-un [::substitute ::with ::weekday])))

(s/def ::date
  (s/keys :req-un [::month ::day]
          :opt-un [::moving-conditions]))

(s/def ::days int?)

;; XML reading

(s/def :xml/tag keyword?)

(s/def :xml/attrs (s/nilable (s/map-of :key :string)))

(s/def :xml/content (s/cat :first-text (s/? string?)
                           :nodes-and-text (s/* (s/cat :node `xml-node
                                                       :text (s/? string?)))))

(s/def xml-node
  (s/keys req-un [:xml/tag :xml/attrs :xml/content]))


(defn read-xml
  ""
  [suffix]
  (-> (str "holidays/Holidays_" (name suffix) ".xml")
      io/resource
      io/input-stream
      xml/parse))

(s/fdef read-xml
  :args (s/cat :suffix #(instance? clojure.lang.Named %))
  :ret `xml-node)


(defn attribute
  ""
  [node attribute-name]
  (get-in node [:attrs attribute-name]))

(s/fdef attribute
  :args (s/cat :node `xml-node :attribute-name keyword?)
  :ret (s/nilable string?))


(defn elements
  ""
  [node tag]
  (let [prefixed (keyword (str "tns" tag))]
    (->> node
         :content
         (filter #(= prefixed (:tag %)))
         seq)))

(s/fdef elements
  :args (s/cat :node `xml-node :tag keyword?)
  :ret (s/nilable (s/coll-of `xml-node)))


(defn element
  ""
  [node tag]
  (first (elements node tag)))

(s/fdef element
  :args (s/cat :node `xml-node :tag keyword?)
  :ret (s/nilable `xml-node))


;; String manipulation

(defn equals-ignore-case
  ""
  [s1 s2]
  (= (-> s1 string/lower-case)
     (-> s2 string/lower-case)))

(s/fdef equals-ignore-case
  :args (s/cat :s1 string? :s2 string?)
  :ret boolean?)


(defn lowercase?
  ""
  [s]
  (= s (string/lower-case s)))

(s/fdef lowercase?
  :args (s/cat :s string?)
  :ret boolean?)


(defn strip
  ""
  [s to-strip]
  (string/replace s to-strip ""))

(s/fdef strip
  :args (s/cat :s string? :to-strip string?)
  :ret string?)


(defn kebab->camel
  ""
  [s]
  (let [[head & tail] (string/split s #"-")]
    (apply str
           head
           (map string/capitalize tail))))

(s/fdef kebab->camel
  :args (s/cat :s string?)
  :ret string?
  :fn #(equals-ignore-case
        (:ret %)
        (-> % :args :s (strip "-"))))


(defn camel->kebab
  ""
  [s]
  (as-> s %
    (string/split % #"(?=[A-Z])")
    (string/join \- %)
    (string/lower-case %)))

(s/fdef camel->kebab
  :args (s/cat :s string?)
  :ret string?
  :fn (s/and
       #(-> % :ret lowercase?)
       #(equals-ignore-case
         (-> % :ret (strip "-"))
         (-> % :args :s))))


;;

(defn parse-attributes
  ""
  [node attribute-fns]
  (reduce
   (fn [res [att f]]
     (let [att (name att)]
       (if-let [v (attribute node (keyword (kebab->camel att)))]
         (assoc res
                (keyword att)
                (f v))
         res)))
   {}
   attribute-fns))

(s/fdef parse-attributes
  :args (s/cat :node `xml-node
               :attribute-fns (s/map-of keyword? ifn?))
  :ret (s/map-of keyword? any?))


(defn ->int
  ""
  [s]
  (Integer/parseInt s))

(s/fdef ->int
  :args (s/cat :s string?)
  :ret int?)


(defn ->keyword
  ""
  [s]
  (-> s
   (string/replace "_" "-")
   string/lower-case
   keyword))

(s/fdef ->keyword
  :args (s/cat :s string?)
  :ret keyword?
  :fn #(equals-ignore-case
        (-> % :ret name (strip "-"))
        (-> % :args :s (strip "_"))))


;;

(s/def ::holiday #{:islamic-holiday
                   :fixed-weekday
                   :hindu-holiday
                   :hebrew-holiday
                   :fixed-weekday-between-fixed
                   :fixed-weekday-relative-to-fixed
                   :relative-to-weekday-in-month
                   :relative-to-fixed
                   :relative-to-easter-sunday
                   :ethiopian-orthodox-holiday
                   :christian-holiday
                   :fixed})

(s/def holiday-common
  (s/keys
   :req-un [::holiday]
   :opt-un [::valid-from ::valid-to ::every ::description-key ::localized-type]))

(defn parse-moving-conditions
  ""
  [node]
  (when-let [conditions (elements node :MovingCondition)]
    {:moving-conditions
     (map #(parse-attributes
            %
            {:substitute ->keyword
             :with       ->keyword
             :weekday    ->keyword})
          conditions)}))

(s/fdef parse-moving-conditions
  :args (s/cat :node `xml-node)
  :ret (s/nilable (s/keys :req-un [::moving-conditions])))


(defmulti -parse-holiday
  ""
  :tag)

(defmulti parser :holiday)

(s/def holiday (s/multi-spec parser :holiday))

(defn tag->holiday
  ""
  [tag]
  (-> tag
      name
      (string/split #":")
      fnext
      camel->kebab
      keyword))

(s/fdef tag->holiday
  :args (s/cat :tag :xml/tag)
  :ret ::holiday)


(defn parse-common-holiday-attributes
  ""
  [node]
  (let [description-key (attribute node :descriptionPropertiesKey)
        holiday         (-> node
                            (parse-attributes
                             {:valid-from      ->int
                              :valid-to        ->int
                              :every           ->keyword
                              :localized-type  ->keyword})
                            (assoc :holiday (-> node :tag tag->holiday)))]
    (if description-key
      (assoc holiday :description-key (->keyword description-key))
      holiday)))

(s/fdef parse-common-holiday-attributes
  :args (s/cat :node `xml-node)
  :ret `holiday-common)


(defn parse-holiday
  ""
  [node]
  (merge
   (parse-common-holiday-attributes node)
   (-parse-holiday node)))

(s/fdef parse-holiday
  :args (s/cat :node `xml-node)
  :ret `holiday)


(defn parse-configuration
  ""
  [configuration]

  (let [holidays           (element configuration :Holidays)
        sub-configurations (elements configuration :SubConfigurations)
        configuration      {:description (attribute configuration :description)
                            :hierarchy   (-> configuration (attribute :hierarchy) ->keyword)
                            :holidays    (mapv parse-holiday (:content holidays))}]
    (if sub-configurations
      (assoc configuration :sub-configurations (mapv parse-configuration sub-configurations))
      configuration)))


(defn read-configuration
  ""
  [calendar]
  (parse-configuration (read-xml calendar)))


(def key-order
  ""
  [;; configuration
   :hierarchy
   :description
   :holidays
   :sub-configurations

   ;; holiday prefix
   :holiday
   ;; fixed
   :month
   :day
   ;; relative prefix
   :when
   :date
   :days
   ;; weekday
   :which
   :weekday
   :from
   :to
   ;; relative suffix
   :fixed-weekday
   :every
   ;; christian / islamic / hebrew / hindu / ethiopian orthodox
   :type
   :chronology
   ;; holiday suffix
   :valid-from
   :valid-to
   :description-key
   :localized-type
   :moving-conditions

   :substitute
   :with])


(defn sort-map
  ""
  [m]
  (let [ks (-> m keys set)
        ordered-keys (filter ks key-order)
        unhandled-keys (remove (set ordered-keys) ks)]
    (when (pos? (count unhandled-keys))
      (throw (Exception. (str "Unhandled keys " (string/join ", " unhandled-keys)))))
    (apply array-map (mapcat #(vector % (% m)) ordered-keys))))


(defn sorted-configuration
  ""
  [calendar-name]
  (try
    (->> calendar-name
         read-configuration
         (postwalk #(if (map? %) (sort-map %) %)))
    (catch Exception e
      (throw (Exception. (str "While reading calendat " (name calendar-name))
                         e)))))


(defn xml->edn
  ""
  [target-dir]
  (binding [pprint/*print-right-margin* 110]
    (doseq [cal  (clojyday/calendars)
            :let [conf (sorted-configuration cal)
                  f    (io/file target-dir (str (name cal) "-holidays.edn"))]]
      (io/make-parents f)
      (pprint conf (io/writer f)))))


;; Fixed day

(defn parse-fixed
  ""
  [node]
  (merge
   (parse-attributes
    node
    {:month ->keyword
     :day   ->int})
   (parse-moving-conditions node)))

(s/fdef parse-fixed
  :args (s/cat :node `xml-node)
  :ret ::date)


(defmethod -parse-holiday :tns:Fixed [node]
  (parse-fixed node))

(defmethod parser :fixed [_]
  (s/merge
   `holiday-common
   ::date))


;; Weekday relative to fixed

(defmethod -parse-holiday :tns:RelativeToFixed [node]
  (let [weekday (-> node (element :Weekday) :content first)
        days    (-> node (element :Days) :content first)
        holiday {:when (-> node (element :When) :content first ->keyword)
                 :date (-> node (element :Date) parse-fixed)}]
    (cond-> holiday
      weekday (assoc :weekday (->keyword weekday))
      days    (assoc :days (->int days)))))

(defmethod parser :relative-to-fixed [_]
  (s/merge
   `holiday-common
   (s/and
    (s/keys :req-un [::when ::date] :opt-un [::days ::weekday])
    #(some % [:days :weekday]))))


(defmethod -parse-holiday :tns:FixedWeekdayBetweenFixed [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword})
      (assoc :from (-> node (element :from) parse-fixed))
      (assoc :to (-> node (element :to) parse-fixed))))

(defmethod parser :fixed-weekday-between-fixed [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::weekday ::from ::to])))


;; Weekday in month

(s/def ::fixed-weekday
  (s/keys :req-un [::which ::weekday ::month]))

(defn parse-fixed-weekday
  ""
  [node]
  (parse-attributes
   node
   {:which   ->keyword
    :weekday ->keyword
    :month   ->keyword}))

(s/fdef parse-fixed-weekday
  :args (s/cat :node `xml-node)
  :ret ::fixed-weekday)


(defmethod -parse-holiday :tns:FixedWeekday [node]
  (parse-fixed-weekday node))

(defmethod parser :fixed-weekday [_]
  (s/merge
   `holiday-common
   ::fixed-weekday))


;; Relative to weekday in month

(defmethod -parse-holiday :tns:RelativeToWeekdayInMonth [node]
  (-> node
      (parse-attributes
       {:weekday ->keyword
        :when    ->keyword})
      (assoc :fixed-weekday (-> node (element :FixedWeekday) parse-fixed-weekday))))

(defmethod parser :relative-to-weekday-in-month [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::weekday ::when ::fixed-weekday])))


;; Weekday relative to fixed day

(defmethod -parse-holiday :tns:FixedWeekdayRelativeToFixed [node]
  (-> node
      (parse-attributes
       {:which ->keyword
        :weekday ->keyword
        :when ->keyword})
      (assoc :date (-> node (element :day) parse-fixed))))

(defmethod parser :fixed-weekday-relative-to-fixed [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::which ::weekday ::when ::date])))


;; Christian

(s/def :christian/type #{:good-friday
                         :easter-monday
                         :ascension-day
                         :whit-monday
                         :corpus-christi
                         :maundy-thursday
                         :ash-wednesday
                         :mardi-gras
                         :general-prayer-day
                         :clean-monday
                         :shrove-monday
                         :pentecost
                         :carnival
                         :easter-saturday
                         :easter-tuesday
                         :sacred-heart
                         :easter
                         :pentecost-monday
                         :whit-sunday})

(s/def ::chronology #{:julian :gregorian})

(defmethod -parse-holiday :tns:ChristianHoliday [node]
  (merge
   (parse-attributes
    node
    {:type       ->keyword
     :chronology ->keyword})
   (parse-moving-conditions node)))

(defmethod parser :christian-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:christian/type] :opt-un [::chronology ::moving-conditions])))


(defmethod -parse-holiday :tns:RelativeToEasterSunday [node]
  {:chronology (-> node (element :chronology) :content first ->keyword)
   :days (-> node (element :days) :content first ->int)})


(defmethod parser :relative-to-easter-sunday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [::chronology ::days])))


;; Islamic

(s/def :islamic/type #{:newyear
                       :aschura
                       :mawlid-an-nabi
                       :lailat-al-miraj
                       :lailat-al-barat
                       :ramadan
                       :lailat-al-qadr
                       :id-al-fitr
                       :id-ul-adha})

(defmethod -parse-holiday :tns:IslamicHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod parser :islamic-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:islamic/type])))


;; Hindu

(s/def :hindu/type #{:holi})

(defmethod -parse-holiday :tns:HinduHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod parser :hindu-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:hindu/type])))


;; Hebrew

(s/def :hebrew/type #{:rosh-hashanah
                      :aseret-yemei-teshuva
                      :yom-kippur
                      :sukkot
                      :shemini-atzeret
                      :hanukkah
                      :asarah-betevet
                      :tu-bishvat
                      :purim
                      :1-nisan
                      :pesach
                      :sefirah
                      :lag-baomer
                      :shavout
                      :17-tammuz
                      :tisha-bav
                      :1-elul
                      :rosh-codesh
                      :shabbat
                      :yom-hashoah
                      :yom-hazikaron
                      :yom-haatzamaut
                      :yom-yerushalaim})

(defmethod -parse-holiday :tns:HebrewHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod parser :hebrew-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:hebrew/type])))


;; Ethiopian orthodox
(s/def :ethiopian-orthodox/type #{:timkat
                                  :enkutatash
                                  :meskel})

(defmethod -parse-holiday :tns:EthiopianOrthodoxHoliday [node]
  (parse-attributes
   node
   {:type ->keyword}))

(defmethod parser :ethiopian-orthodox-holiday [_]
  (s/merge
   `holiday-common
   (s/keys :req-un [:ethiopian-orthodox/type])))

;; Copyright 2018 Frederic Merizen
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
;; or implied. See the License for the specific language governing
;; permissions and limitations under the License.
