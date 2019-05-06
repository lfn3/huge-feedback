(ns huge-feedback.config
  (:require
    [clojure.tools.reader.edn :as edn]
    [huge-feedback.apis.gitlab :as gitlab]
    [re-frame.core :as rf]
    [reagent.core :as r]
    [huge-feedback.util :as util]))

(defn text-editor [initial-value on-save & [validate]]
  (let [state (r/atom {:input initial-value :valid? true})]
    (fn []
      [:form
       [:textarea {:class         (if (:valid? @state) "" "invalid")
                   :on-change     #(->> %1 (.-target) (.-value) (swap! state assoc :input))
                   :default-value initial-value}]
       [:input {:type     "submit"
                :on-click #(let [{:keys [input]} @state]
                             (.preventDefault %1)
                             (if (or (nil? validate) (validate input))
                               (on-save input)
                               (swap! state assoc :valid? false)))}]])))

(defn panel []
  [:div
   [text-editor (str @(rf/subscribe [:config]))
    #(rf/dispatch [:config (edn/read-string %1)])
    #(try (let [val (edn/read-string %1)]
            (gitlab/valid-config? val))
          (catch js/Error e
            false))]])
