(ns huge-feedback.util
  (:require [cljs.pprint]))

(defn display-html-debug [x]
  [:pre [:code (with-out-str (cljs.pprint/pprint x))]])
