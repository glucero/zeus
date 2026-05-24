(ns zeus.extract-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [zeus.extract :as ex]))

(defn- temp-dir []
  (doto (java.io.File/createTempFile "zeus-ex" "")
    (.delete) (.mkdir) (.deleteOnExit)))

(deftest which
  (testing "returns absolute path for a binary on PATH"
    (is (.exists (io/file (ex/which "sh"))))
    (is (.isAbsolute (io/file (ex/which "sh")))))
  (testing "nil for unknown binary"
    (is (nil? (ex/which "nope-not-a-binary-12345")))))

(deftest extract-psp
  (testing "calls pkg2zip with the pkg file and returns produced ISO"
    (let [dir (temp-dir)
          pkg (doto (io/file dir "game.pkg") (spit "x"))
          calls (atom [])]
      (with-redefs [ex/which (constantly "/fake/pkg2zip")
                    ex/run!  (fn [args opts]
                               (swap! calls conj [args opts])
                               (spit (io/file (:dir opts) "GAME.ISO") "iso")
                               true)]
        (let [out (ex/extract-psp pkg dir)]
          (is (= "GAME.ISO" (.getName out)))
          (is (= "/fake/pkg2zip" (get-in @calls [0 0 0])))))))
  (testing "returns nil when pkg2zip is not installed"
    (with-redefs [ex/which (constantly nil)]
      (is (nil? (ex/extract-psp (io/file "x.pkg") (temp-dir)))))))

(deftest extract-psx
  (testing "runs pkg2zip -x then psxtract -c"
    (let [dir (temp-dir)
          pkg (doto (io/file dir "game.pkg") (spit "x"))
          eboot-dir (doto (io/file dir "pspemu") .mkdirs)
          calls (atom [])]
      (with-redefs [ex/which (fn [b] (str "/fake/" b))
                    ex/run!  (fn [args opts]
                               (swap! calls conj (first args))
                               (cond
                                 (= "/fake/pkg2zip" (first args))
                                 (spit (io/file eboot-dir "EBOOT.PBP") "eb")
                                 (= "/fake/psxtract" (first args))
                                 (spit (io/file (:dir opts) "GAME.bin") "bin"))
                               true)]
        (let [out (ex/extract-psx pkg dir)]
          (is (= ["/fake/pkg2zip" "/fake/psxtract"] @calls))
          (is (= "GAME.bin" (.getName out)))))))
  (testing "returns nil when either tool is missing"
    (with-redefs [ex/which (fn [b] (when (= b "pkg2zip") "/fake/pkg2zip"))]
      (is (nil? (ex/extract-psx (io/file "x.pkg") (temp-dir)))))))
