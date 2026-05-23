(ns zeus.platforms-test
  (:require [clojure.test :refer [deftest is testing]]
            [zeus.platforms :as p]))

(deftest platforms-map
  (testing "every supported platform is keyed"
    (is (= #{:psv :ps3 :psp :psx :psm} (set (keys p/platforms)))))
  (testing "each platform lists at least one content type"
    (doseq [[plat types] p/platforms]
      (is (seq types) (str plat " has no content types")))))

(deftest content-types-derived
  (is (= (set (mapcat val p/platforms)) (set p/content-types)))
  (is (contains? (set p/content-types) :ps3_games))
  (is (contains? (set p/content-types) :psv_dlcs)))

(deftest regions
  (is (= [:us :eu :jp :asia] p/regions)))

(deftest platform-folders
  (is (= "psvita" (p/platform-folders :psv)))
  (is (= "ps3" (p/platform-folders :ps3))))
