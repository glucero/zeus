(ns zeus.firmware
  (:require [babashka.http-client :as http]
            [clojure.string :as str]))

(def ps3-updatelist-url
  "http://dus01.ps3.update.playstation.net/update/ps3/list/us/ps3-updatelist.txt")

(defn fetch-text
  "GET `url` and return the response body as a string. Pulled out so
   tests can redef it."
  [url]
  (-> (http/get url) :body))

(defn ps3-pup-url
  "Resolve the URL of the current PS3 firmware PUP by parsing Sony's
   update-list endpoint. Returns nil if no PS3UPDAT.PUP line is found."
  []
  (let [body (fetch-text ps3-updatelist-url)
        line (->> (str/split-lines body)
                  (filter (fn [l] (re-find #"PS3UPDAT\.PUP" l)))
                  first)]
    (some->> line (re-find #"CDN=([^;]+)") second)))
