;; Copyright and license information at end of file

(ns clojyday.place-test

  (:require
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [clojyday.jaxb-utils :refer [jaxb-fixture]]
   [clojyday.place :as place]
   [clojyday.spec-test-utils :refer [instrument-fixture]]
   [clojyday.util :as util])

  (:import
    (de.jollyday HolidayCalendar)
    (de.jollyday.parameter UrlManagerParameter)
    (java.util Locale)))


;; Fixtures

(use-fixtures :once
  (join-fixtures [jaxb-fixture instrument-fixture]))


;; Basic type predicates

(deftest locale?-test
  (testing "only instances of Locale are locales"
    (is (place/locale? (Locale/FRANCE)))
    (is (not (place/locale? :france)))))


(deftest format?-test
  (is (place/format? :any-format))
  (is (place/format? :xml))
  (is (place/format? :xml-jaxb))
  (is (not (place/format? :random))))


;;



(deftest format-properties-test
  (let [old-hierarchy @#'place/format-hierarchy]
    (with-redefs [place/format-hierarchy (-> (make-hierarchy)
                                             (derive :foo :foo/bar)
                                             (derive :foo/bar :any-format))]
      (testing "with namespaced keyword"
        (is (= :foo/bar
               (-> (doto (UrlManagerParameter. nil nil)
                     (place/set-format! :foo/bar))
                   place/get-format))))
      (testing "with plain keyword"
        (is (= :foo
               (-> (doto (UrlManagerParameter. nil nil)
                     (place/set-format! :foo))
                   place/get-format)))))))


;; Parsing a place

(deftest holiday-manager-test
  (testing "With a locale keyword identifier"
    (is (= "fr"
           (-> (place/holiday-manager :xml-jaxb :fr)
               (.getCalendarHierarchy) (.getId)))))
  (testing "With a locale string identifier"
    (is (= "fr"
           (-> (place/holiday-manager :xml-jaxb "fr")
               (.getCalendarHierarchy) (.getId)))))
  (testing "With a Java locale"
    (is (= "fr"
           (-> (place/holiday-manager :xml-jaxb Locale/FRANCE)
               (.getCalendarHierarchy) (.getId)))))
  (testing "With a an existing holiday calendar"
    (is (= "fr"
           (-> (place/holiday-manager :xml-jaxb (HolidayCalendar/FRANCE))
               (.getCalendarHierarchy) (.getId)))))
  (testing "With a configuration file URL"
    "TODO"))


(deftest parse-place-test
  (testing "With a single arument, give holidays for the whole country"
    (let [{::place/keys [manager zones]} (place/parse-place :xml-jaxb :fr)]

      (is (util/string-array? zones))
      (is (nil? (seq zones)))
      (is (= (place/holiday-manager :xml-jaxb :fr) manager))))

  (testing "More arguments are translated into zones"
    (let [{::place/keys [manager zones]} (place/parse-place :xml-jaxb [:fr :br])]

      (is (util/string-array? zones))
      (is (= ["br"]  (seq zones)))
      (is (= (place/holiday-manager :xml-jaxb :fr) manager)))))


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
