(ns huge-feedback.apis.test-gitlab
  (:require [huge-feedback.apis.gitlab :as gitlab]
            [clojure.test :refer [deftest is]]))

(deftest test-get-mr-id-from-ref
  (is (nil? (gitlab/get-mr-iid-from-ref "master")))
  (is (nil? (gitlab/get-mr-iid-from-ref "12-9-auto-deploy-20200226")))
  (is (= 26062
         (gitlab/get-mr-iid-from-ref "refs/merge-requests/26062/merge"))))