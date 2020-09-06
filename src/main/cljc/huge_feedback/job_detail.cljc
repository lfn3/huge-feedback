(ns huge-feedback.job-detail
  (:require
            [reagent.core :as r]
            [re-frame.core :as rf]
            #?@(:cljs [[reagent.dom :as rd]])))

(defn job-chart [data]
  {:data    {:values data}
   :facet   {:row {:field "name" :type "nominal" :spacing 5 :header {:labelAngle 0
                                                                     :labelAlign "left"
                                                                     :title      nil}}}
   :spec    {:layer  [{:encoding {:x     {:field "pipeline-id" :type "nominal"
                                          :axis {:orient "top" :labelAngle -60}}
                                  :y     {:field "duration"
                                          :type "quantitative"
                                          :scale {:zero false}
                                          :axis {:title nil :grid false}}
                                  :color {:field  "status"
                                          :legend {:direction "horizontal" :orient "top"}
                                          :scale  {:domain ["success" "failed" "running" "pending" "manual" "skipped" "canceled" "created"]
                                                   :range  ["#108548" "#dd2b0e" "#428fdc" "#fc9403" "#c4c4c4" "#c4c4c4" "#c4c4c4" "#c4c4c4"]}}
                                  :href  {:field "web_url" :type "nominal"}}
                       :mark     {:type "point" :filled true}
                       :transform [{:filter {:field "duration" :gt 0}}]}
                      #_{:encoding {:x {:field "pipeline-id" :type "nominal"}
                                  :y {:field     "duration"
                                      :type      "quantitative"
                                      :scale     {:zero false}
                                      :axis      {:title nil :grid false}}}
                       :mark     {:type "line"}}]

             :height 15
             :width  "container"}
   :resolve {:scale {:y "independent"}}})

(defn chart [data]
  #?(:clj  "Chart would go here"
     :cljs (let [jsd (clj->js data)
                 opts (clj->js {:mode "vega-lite"
                                :renderer :canvas
                                :loader {:target "_blank"}})]
             (r/create-class
               {:component-did-mount (fn [this]
                                       (js/vegaEmbed (rd/dom-node this) jsd opts))
                :reagent-render      (fn [_] [:div.vega-chart "Chart would go here with data: " (pr-str jsd)])}))))

(defn jobs-chart-fmt [{:keys [jobs] :as _db}]
  (->> jobs (vals) (mapcat vals)))

(rf/reg-sub :jobs-chart-fmt jobs-chart-fmt)

(defn panel []
  (let [job-chart-data @(rf/subscribe [:jobs-chart-fmt])]
   [:div.job-detail-chart
    [chart (job-chart job-chart-data)]]))