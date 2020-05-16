(ns huge-feedback.util
  (:require #?(:cljs [cljs.pprint :as pp]
               :clj [clojure.pprint :as pp])))

(defn display-html-debug [x]
  [:pre [:code (with-out-str (pp/pprint x))]])
