;; Copyright and license information at end of file

(ns ferje.config.core-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.walk :refer [prewalk]]
   [ferje.core :as ferje]
   [ferje.config.core :as config]
   [ferje.place :as place]
   [ferje.spec-test-utils :refer [instrument-fixture]]
   [java-time :as time])
  (:import
   (de.jollyday.config ChristianHoliday ChristianHolidayType ChronologyType Configuration
                       EthiopianOrthodoxHoliday EthiopianOrthodoxHolidayType
                       Fixed FixedWeekdayBetweenFixed FixedWeekdayInMonth
                       FixedWeekdayRelativeToFixed HebrewHoliday HinduHoliday
                       HinduHolidayType Holidays HolidayType IslamicHoliday
                       IslamicHolidayType Month MovingCondition RelativeToEasterSunday
                       RelativeToFixed RelativeToWeekdayInMonth Weekday When Which With)))


;; Fixtures

(use-fixtures :once instrument-fixture)


;;

(defn config?
  "is x a Jollyday configuration object?"
  [x]
  (and x
       (= "de.jollyday.config"
          (-> x class .getPackage .getName))))

(defn config-bean
  "Create a clojure persistent map / vector representation
  of a given Jollyday configuration bean"
  [x]
  (prewalk
   #(cond
      (or (instance? Enum %) (string? %) (nil? %)) %
      (config? %)                                  (into {} (bean %))
      (seqable? %)                                 (vec %)
      :else                                        %)
   x))

(deftest named?-test
  (is (config/named? :a))
  (is (config/named? 'a))
  (is (config/named? "a")))

(deftest ->const-name-test
  (is (= "OCTOBER_DAYE"
         (config/->const-name :october-daye))))

(deftest ->enum-test
  (is (= Month/AUGUST
         (config/->enum :august Month))))

(deftest set-common-holiday-attributes!-test
  (testing "Respect default values"
    (is (= (config-bean (doto (Fixed.)
                          (.setEvery "EVERY_YEAR")
                          (.setLocalizedType HolidayType/OFFICIAL_HOLIDAY)))
           (config-bean (doto (Fixed.)
                          (config/set-common-holiday-attributes! {})))))))

(deftest ->Holiday-test
    (testing "For fixed"
      (is (= (config-bean (doto (Fixed.)
                            (.setMonth Month/FEBRUARY)
                            (.setDay (int 28))))

             (config-bean (config/->Holiday
                           {:holiday :fixed, :month :february, :day 28}))))

      (is (= (config-bean (doto (Fixed.)
                            (.setMonth Month/FEBRUARY)
                            (.setDay (int 28))
                            (-> (.getMovingCondition)
                                (.add (doto (MovingCondition.)
                                        (.setSubstitute Weekday/SATURDAY)
                                        (.setWith With/NEXT)
                                        (.setWeekday Weekday/MONDAY))))))

             (config-bean (config/->Holiday
                           {:holiday           :fixed
                            :month             :february
                            :day               28
                            :moving-conditions [{:substitute :saturday,
                                                 :with       :next,
                                                 :weekday    :monday}]})))))

    (testing "For relative to fixed"
      (is (= (config-bean (doto (RelativeToFixed.)
                            (.setDescriptionPropertiesKey "VICTORIA_DAY")
                            (.setWeekday Weekday/MONDAY)
                            (.setWhen When/BEFORE)
                            (.setDate (doto (Fixed.)
                                        (.setMonth Month/MAY)
                                        (.setDay (int 24))))))
             (config-bean (config/->Holiday
                           {:holiday         :relative-to-fixed
                            :description-key :victoria-day
                            :weekday         :monday
                            :when            :before
                            :date            {:month :may, :day 24}}))))

      (is (= (config-bean (doto (RelativeToFixed.)
                            (.setDays (int 5))
                            (.setWhen When/AFTER)
                            (.setDate (doto (Fixed.)
                                        (.setMonth Month/NOVEMBER)
                                        (.setDay (int 23))))))
             (config-bean (config/->Holiday
                           {:holiday :relative-to-fixed
                            :days    5
                            :when    :after
                            :date    {:month :november, :day 23}})))))

    (testing "For fixed weekday in month"
      (is (= (config-bean (doto (FixedWeekdayInMonth.)
                            (.setWhich Which/LAST)
                            (.setWeekday Weekday/MONDAY)
                            (.setMonth Month/MAY)
                            (.setValidFrom (int 1968))
                            (.setDescriptionPropertiesKey "MEMORIAL")))
             (config-bean (config/->Holiday
                           {:holiday         :fixed-weekday
                            :which           :last
                            :weekday         :monday
                            :month           :may
                            :valid-from      1968
                            :description-key :memorial})))))

    (testing "For relative to weekday in month"
      (is (= (config-bean (doto (RelativeToWeekdayInMonth.)
                            (.setWeekday Weekday/TUESDAY)
                            (.setWhen When/AFTER)
                            (.setDescriptionPropertiesKey "ELECTION")
                            (.setFixedWeekday (doto (FixedWeekdayInMonth.)
                                                (.setWhich Which/FIRST)
                                                (.setWeekday Weekday/MONDAY)
                                                (.setMonth Month/MAY)))))
             (config-bean (config/->Holiday
                           {:holiday         :relative-to-weekday-in-month
                            :weekday         :tuesday
                            :when            :after
                            :description-key :election
                            :fixed-weekday   {:which   :first
                                              :weekday :monday
                                              :month   :may}})))))

    (testing "For christian holiday"
      (is (= (config-bean (doto (ChristianHoliday.)
                            (.setType ChristianHolidayType/CLEAN_MONDAY)))
             (config-bean (config/->Holiday
                           {:holiday :christian-holiday
                            :type    :clean-monday}))))

      (is (= (config-bean (doto (ChristianHoliday.)
                            (.setType ChristianHolidayType/CLEAN_MONDAY)
                            (.setChronology ChronologyType/JULIAN)))
             (config-bean (config/->Holiday
                           {:holiday    :christian-holiday
                            :type       :clean-monday
                            :chronology :julian}))))

      (is (= (config-bean (doto (ChristianHoliday.)
                            (.setType ChristianHolidayType/CLEAN_MONDAY)
                            (.setChronology ChronologyType/JULIAN)
                            (-> (.getMovingCondition)
                                (.add (doto (MovingCondition.)
                                        (.setSubstitute Weekday/SATURDAY)
                                        (.setWith With/NEXT)
                                        (.setWeekday Weekday/MONDAY))))))
             (config-bean (config/->Holiday
                           {:holiday           :christian-holiday
                            :type              :clean-monday
                            :chronology        :julian
                            :moving-conditions [{:substitute :saturday,
                                                 :with       :next,
                                                 :weekday    :monday}]})))))

    (testing "For islamic holiday"
      (is (= (config-bean (doto (IslamicHoliday.)
                            (.setType IslamicHolidayType/ID_UL_ADHA)))
             (config-bean (config/->Holiday
                           {:holiday :islamic-holiday
                            :type    :id-ul-adha})))))

    (testing "For fixed weekday between fixed"
      (is (= (config-bean (doto (FixedWeekdayBetweenFixed.)
                            (.setWeekday Weekday/SATURDAY)
                            (.setDescriptionPropertiesKey "ALL_SAINTS")
                            (.setFrom (doto (Fixed.)
                                        (.setMonth Month/OCTOBER)
                                        (.setDay (int 31))))
                            (.setTo (doto (Fixed.)
                                      (.setMonth Month/NOVEMBER)
                                      (.setDay (int 6))))))
             (config-bean (config/->Holiday
                           {:holiday         :fixed-weekday-between-fixed
                            :weekday         :saturday
                            :description-key :all-saints
                            :from            {:month :october, :day 31}
                            :to              {:month :november, :day 6}})))))

    (testing "For fixed weekday relative to fixed"
      (is (= (config-bean (doto (FixedWeekdayRelativeToFixed.)
                            (.setWhich Which/FIRST)
                            (.setWeekday Weekday/THURSDAY)
                            (.setWhen When/AFTER)
                            (.setDescriptionPropertiesKey "FIRST_DAY_SUMMER")
                            (.setDay (doto (Fixed.)
                                       (.setMonth Month/APRIL)
                                       (.setDay (int 18))))))
             (config-bean (config/->Holiday
                           {:holiday         :fixed-weekday-relative-to-fixed
                            :which           :first
                            :weekday         :thursday
                            :when            :after
                            :description-key :first-day-summer
                            :date            {:month :april, :day 18}})))))

    (testing "For hindu holiday"
      (is (= (config-bean (doto (HinduHoliday.)
                            (.setType HinduHolidayType/HOLI)))
             (config-bean (config/->Holiday
                           {:holiday :hindu-holiday
                            :type    :holi})))))

    (testing "For hebrew holiday"
      (is (= (config-bean (doto (HebrewHoliday.)
                            (.setType "YOM_KIPPUR")))
             (config-bean (config/->Holiday
                           {:holiday :hebrew-holiday
                            :type    :yom-kippur})))))

    (testing "For ethiopian orthodox holiday"
      (is (= (config-bean (doto (EthiopianOrthodoxHoliday.)
                            (.setType EthiopianOrthodoxHolidayType/TIMKAT)))
             (config-bean (config/->Holiday
                           {:holiday :ethiopian-orthodox-holiday
                            :type    :timkat})))))

    (testing "For relative to easter sunday"
      (is (= (config-bean (doto (RelativeToEasterSunday.)
                            (.setChronology ChronologyType/JULIAN)
                            (.setDays (int 12))))
             (config-bean (config/->Holiday
                           {:holiday    :relative-to-easter-sunday
                            :chronology :julian
                            :days       12}))))))


(deftest ->Configuration-test
  (is (= (config-bean (doto (Configuration.)
                        (.setDescription "France")
                        (.setHierarchy "fr")
                        (.setHolidays
                         (doto (Holidays.)
                           (-> (.getFixed)
                               (.add (doto (Fixed.)
                                       (.setDescriptionPropertiesKey "NEW_YEAR")
                                       (.setMonth Month/JANUARY)
                                       (.setDay (int 1)))))))
                        (-> (.getSubConfigurations)
                            (.add
                             (doto (Configuration.)
                               (.setDescription "Martinique")
                               (.setHierarchy "ma")
                               (.setHolidays
                                (doto (Holidays.)
                                  (->
                                   (.getChristianHoliday)
                                   (.add
                                    (doto (ChristianHoliday.)
                                      (.setType ChristianHolidayType/CLEAN_MONDAY)))))))))))
         (config-bean (config/->Configuration
                       {:description "France",
                        :hierarchy   :fr,
                        :holidays
                        [{:holiday         :fixed,
                          :description-key :new-year,
                          :month           :january,
                          :day             1}]
                        :sub-configurations
                        [{:description "Martinique",
                          :hierarchy   :ma,
                          :holidays    [{:holiday :christian-holiday, :type :clean-monday}]}]})))))


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
