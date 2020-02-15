(ns huge-feedback.pipelines
  (:require [re-frame.core :as rf]))

(rf/reg-sub :latest
  (fn [{:keys [pipelines]} [_ ref]]
    (->> pipelines
         (vals)
         (filter (comp (partial = ref) :ref))
         (sort-by :id)
         (last))))

(rf/reg-sub :rest
  (fn [{:keys [pipelines]} [_ ref]]
    (->> pipelines
         (vals)
         (filter (comp (partial = ref) :ref))
         (sort-by :id)
         (reverse)
         (rest))))

(rf/reg-sub :non-master
  (fn [{:keys [pipelines]}] (->> pipelines
                                 (vals)
                                 (filter (comp (partial not= "master") :ref)))))

(rf/reg-sub :refs
  (fn [{:keys [pipelines]}] (->> pipelines
                                 (vals)
                                 (map :ref)
                                 (distinct))))

;TODO: add count of jobs pending/running/passed/failed?
(defn pipeline-stage-html [[stage-name {:keys [status]}]]
  [:div {:class (str "pipeline-stage " status)} stage-name])

(defn pipeline-html [pipeline]
  [:div
   [:a.block {:href (:web_url pipeline)}
    [:div (str (:ref pipeline) \@ (:sha pipeline))]
    [:div.pipeline
     (for [stage-state @(rf/subscribe [:stage-state-for-pipeline (:id pipeline)])]
       (pipeline-stage-html stage-state))]]])

(defn mini-pipeline-html [pipeline]
  [:div
   [:a.block {:href (:web_url pipeline)}
    [:div.pipeline.mini
     (for [stage-state @(rf/subscribe [:stage-state-for-pipeline (:id pipeline)])]
       (pipeline-stage-html stage-state))]]])

(defn panel []
  (if-let [master @(rf/subscribe [:latest "master"])]
    [:div
     [pipeline-html master]
     (for [pipeline @(rf/subscribe [:rest "master"])]
       [mini-pipeline-html pipeline])
     (for [ref (->> @(rf/subscribe [:refs])
                    (filter (partial not= "master")))]
       [:div
        [pipeline-html @(rf/subscribe [:latest ref])]
        (for [rest @(rf/subscribe [:rest ref])]
          [mini-pipeline-html rest])])]
    [:p "Fetching pipelines..."]))