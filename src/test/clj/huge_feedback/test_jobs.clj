(ns huge-feedback.test-jobs
  (:require [clojure.test :refer :all]
            [huge-feedback.pipeline-detail :as jobs]))

(deftest should-handle-empty-jobs-list
  (is (= (jobs/body nil nil) [:tbody])))