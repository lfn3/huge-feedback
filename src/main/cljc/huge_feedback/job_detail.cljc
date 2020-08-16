(ns huge-feedback.job-detail
  (:require [huge-feedback.job-utils :as job-utils]
            [clojure.set :as set]
            [reagent.core :as r]
            [re-frame.core :as rf]
            #?@(:cljs [[reagent.dom :as rd]])
            [huge-feedback.util :as util]))

(defn job-chart [data]
  {:data     {:values data}
   :height   15
   :width    "container"
   :encoding {:x     {:field "pipeline-id" :type "nominal"}
              :y     {:field "duration" :type "quantitative" :scale {:zero false} :axis {:title nil :grid false}}
              :color {:field "status" :legend {:direction "horizontal" :orient "top"}}
              :href  {:field "web_url" :type "nominal"}
              :row   {:field "name" :type "nominal" :spacing 5 :header {:labelAngle 0
                                                                        :labelAlign "left"
                                                                        :title      nil}}}
   :resolve  {:scale {:y "independent"}}
   :mark     {:type "point" :filled true}})

(defn chart [data]
  #?(:clj  "Chart would go here"
     :cljs (let [jsd (clj->js data)
                 opts (clj->js {:mode "vega-lite"
                                :renderer :canvas})]
             (r/create-class
               {:component-did-mount (fn [this]
                                       (js/vegaEmbed (rd/dom-node this) jsd opts))
                :reagent-render      (fn [_] [:div.vega-chart "Chart would go here with data: " (pr-str jsd)])}))))

(defn jobs-chart-fmt [{:keys [jobs] :as db}]
  (->> jobs
       (vals)
       (mapcat vals)
       (map (fn [{:keys [pipeline] :as v}] (assoc v :pipeline-id (:id pipeline))))
       (map #(select-keys %1 [:name :pipeline-id :stage :duration :status :web_url]))))

(rf/reg-sub :jobs-chart-fmt jobs-chart-fmt)

(defn panel []
  (let [job-chart-data @(rf/subscribe [:jobs-chart-fmt])]
   [:div.job-detail-chart
    [chart (job-chart job-chart-data)]]))