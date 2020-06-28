(ns huge-feedback.config
  (:require [mount.core :as mount]
            [clojure.tools.reader.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(defn read-local-config [config-path]
  (->> config-path
       (io/file)
       (io/reader)
       (PushbackReader.)
       (edn/read)))

(mount/defstate local-config
  :start (read-local-config (mount/args)))